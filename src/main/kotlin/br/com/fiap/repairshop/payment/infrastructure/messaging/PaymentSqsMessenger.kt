package br.com.fiap.repairshop.payment.infrastructure.messaging

import br.com.fiap.repairshop.payment.infrastructure.health.SqsListenerHealthIndicator
import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoClient
import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoPaymentRequest
import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.models.InvoiceDynamoItem
import br.com.fiap.repairshop.payment.infrastructure.persistence.models.PaymentDynamoItem
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.time.Instant
import java.util.UUID

data class OsReadyForPaymentInboundEvent(
    val orderId: UUID,
    val customerId: UUID,
    val amount: Double
)

data class PaymentSuccessEvent(
    val orderId: UUID,
    val paymentId: String,
    val status: String,
    val amount: Double
)

data class PaymentNotificationEvent(
    val eventType: String, // ready_for_payment, payment_approved, payment_refused
    val orderId: UUID,
    val customerId: UUID,
    val paymentId: String,
    val amount: Double,
    val status: String,
    val qrCode: String? = null,
    val ticketUrl: String? = null
)

@Component
class PaymentSqsMessenger(
    private val sqsClient: SqsClient,
    private val paymentRepository: DynamoPaymentRepository,
    private val mercadoPagoClient: MercadoPagoClient,
    private val objectMapper: ObjectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().findAndRegisterModules(),
    @Value("\${sqs.queue.os-ready-for-payment:os-ready-for-payment-queue-dev}")
    private val osReadyForPaymentQueue: String,
    @Value("\${sqs.queue.payment-notification:payment-notification-queue-dev}")
    private val paymentNotificationQueue: String,
    @Value("\${sqs.queue.payment-success:payment-success-queue-dev}")
    private val paymentSuccessQueue: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        // Registra a fila de entrada para monitoramento de liveness
        SqsListenerHealthIndicator.registerMonitoredQueue(osReadyForPaymentQueue, 120)
    }

    private fun getQueueUrl(queueName: String): String {
        return runCatching {
            sqsClient.getQueueUrl { it.queueName(queueName) }.queueUrl()
        }.getOrElse { queueName }
    }

    /**
     * Polling da fila oficial: os-ready-for-payment-queue
     * Recebe ordens de serviço finalizadas da OS para gerar cobrança e integrar ao Mercado Pago
     */
    @Scheduled(fixedDelay = 5000)
    fun pollOsReadyForPayment() {
        val queueUrl = getQueueUrl(osReadyForPaymentQueue)
        runCatching {
            val response = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(2)
                    .messageAttributeNames("All")
                    .build()
            )

            SqsListenerHealthIndicator.recordHeartbeat(osReadyForPaymentQueue)

            for (msg in response.messages()) {
                val event = objectMapper.readValue(msg.body(), OsReadyForPaymentInboundEvent::class.java)
                val traceparent = msg.messageAttributes()["traceparent"]?.stringValue()

                logger.info("Processando OS pronta para cobrança: OS={}, Cliente={}, Valor={}", event.orderId, event.customerId, event.amount)

                // 1. Chama Mercado Pago protegido por Circuit Breaker
                val mpResponse = mercadoPagoClient.createPayment(
                    MercadoPagoPaymentRequest(
                        orderId = event.orderId,
                        customerId = event.customerId,
                        amount = event.amount
                    )
                )

                val paymentId = mpResponse.paymentId
                val ts = Instant.now().toEpochMilli()

                // 2. Grava item_payment no DynamoDB tb-payment
                val payment = PaymentDynamoItem(
                    pk = "BILLING#${event.orderId}",
                    sk = "PAY#$ts#$paymentId",
                    entityType = "PAYMENT",
                    paymentId = paymentId,
                    serviceOrderId = event.orderId.toString(),
                    customerId = event.customerId.toString(),
                    priceValue = event.amount,
                    paymentDate = Instant.now().toString(),
                    status = mpResponse.status,
                    paymentMethod = if (mpResponse.isFallback) "MERCADO_PAGO_CONTINGENCY" else "MERCADO_PAGO_QR",
                    osUpdateStatus = "PENDING",
                    paymentInfo = mpResponse.qrCode,
                    dispatchNotificateStatus = "PENDING",
                    originTraceId = traceparent
                )
                paymentRepository.savePayment(payment)

                // 3. Grava item_invoice no DynamoDB tb-payment
                val invoiceId = UUID.randomUUID().toString()
                val invoiceNumber = "INV-${Instant.now().epochSecond.toString().takeLast(6)}"
                val invoice = InvoiceDynamoItem(
                    pk = "BILLING#${event.orderId}",
                    sk = "INV#$ts#$invoiceId",
                    entityType = "INVOICE",
                    invoiceId = invoiceId,
                    serviceOrderId = event.orderId.toString(),
                    customerId = event.customerId.toString(),
                    invoiceNumber = invoiceNumber,
                    priceValue = event.amount,
                    emissionDate = Instant.now().toString(),
                    invoiceInfo = "Fatura emitida para OS ${event.orderId}",
                    clientDispatch = "PENDING"
                )
                paymentRepository.saveInvoice(invoice)

                // 4. Publica evento na payment-notification-queue (ready_for_payment) para o notification-service
                notifyPaymentStatus(
                    eventType = "ready_for_payment",
                    orderId = event.orderId,
                    customerId = event.customerId,
                    paymentId = paymentId,
                    amount = event.amount,
                    status = "PENDING",
                    qrCode = mpResponse.qrCode,
                    ticketUrl = mpResponse.ticketUrl
                )

                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build()
                )
                logger.info("OS {} faturada com sucesso e notificação enviada", event.orderId)
            }
        }.onFailure { ex ->
            logger.warn("Erro ao ler fila {}: {}", osReadyForPaymentQueue, ex.message)
        }
    }

    /**
     * Scheduler oficial do desenho: scheduler_os_payment_atualization (a cada 15 min por padrão)
     * Varre pagamentos aprovados pendentes de sincronização com a OS e publica em payment-success-queue
     */
    @Scheduled(fixedDelayString = "\${scheduler.os-payment.delay:900000}", initialDelay = 20000)
    fun schedulerOsPaymentAtualization() {
        logger.info("Executando scheduler_os_payment_atualization...")
        val pendingUpdates = paymentRepository.findPendingOsUpdates()
        for (payment in pendingUpdates) {
            runCatching {
                val orderId = UUID.fromString(payment.serviceOrderId)
                sendPaymentSuccessEvent(orderId, payment.paymentId, payment.priceValue)

                // Atualiza o item no DynamoDB para evitar reenvio
                paymentRepository.updateStatus(
                    pk = payment.pk,
                    sk = payment.sk,
                    newStatus = "APPROVED",
                    dispatchStatus = "DISPATCHED",
                    osUpdateStatus = "UPDATED"
                )
                logger.info("Pagamento da OS {} sincronizado com sucesso para payment-success-queue", orderId)
            }.onFailure { ex ->
                logger.error("Falha ao despachar atualização de pagamento da OS {}: {}", payment.serviceOrderId, ex.message)
            }
        }
    }

    /**
     * Publica confirmação de pagamento na fila payment-success-queue (consumida pelo service-order-execution-service)
     */
    fun sendPaymentSuccessEvent(orderId: UUID, paymentId: String, amount: Double) {
        val queueUrl = getQueueUrl(paymentSuccessQueue)
        val event = PaymentSuccessEvent(orderId, paymentId, "APPROVED", amount)
        val body = objectMapper.writeValueAsString(event)

        val currentSpan = Span.current()
        val traceparent = "00-${currentSpan.spanContext.traceId}-${currentSpan.spanContext.spanId}-01"

        val request = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(body)
            .messageAttributes(mapOf(
                "traceparent" to MessageAttributeValue.builder().dataType("String").stringValue(traceparent).build()
            ))
            .build()

        sqsClient.sendMessage(request)
        logger.info("Publicado evento em payment-success-queue para OS {} (traceparent: {})", orderId, traceparent)
    }

    /**
     * Publica evento para o notification-service na fila payment-notification-queue
     */
    fun notifyPaymentStatus(
        eventType: String,
        orderId: UUID,
        customerId: UUID,
        paymentId: String,
        amount: Double,
        status: String,
        qrCode: String? = null,
        ticketUrl: String? = null
    ) {
        val queueUrl = getQueueUrl(paymentNotificationQueue)
        val event = PaymentNotificationEvent(
            eventType = eventType,
            orderId = orderId,
            customerId = customerId,
            paymentId = paymentId,
            amount = amount,
            status = status,
            qrCode = qrCode,
            ticketUrl = ticketUrl
        )
        val body = objectMapper.writeValueAsString(event)

        val currentSpan = Span.current()
        val traceparent = "00-${currentSpan.spanContext.traceId}-${currentSpan.spanContext.spanId}-01"

        val request = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(body)
            .messageAttributes(mapOf(
                "traceparent" to MessageAttributeValue.builder().dataType("String").stringValue(traceparent).build()
            ))
            .build()

        sqsClient.sendMessage(request)
        logger.info("Publicado evento {} para OS {} em payment-notification-queue", eventType, orderId)
    }
}
