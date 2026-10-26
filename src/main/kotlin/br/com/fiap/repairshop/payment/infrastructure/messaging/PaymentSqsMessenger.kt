package br.com.fiap.repairshop.payment.infrastructure.messaging

import br.com.fiap.repairshop.payment.infrastructure.health.SqsListenerHealthIndicator
import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.PaymentDynamoItem
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
import java.util.UUID

data class PaymentOrderInboundEvent(
    val orderId: UUID,
    val customerId: UUID,
    val amount: Double
)

data class PaymentCompletedCallbackEvent(
    val orderId: UUID,
    val paymentId: String,
    val status: String
)

data class PaymentReceiptNotificationEvent(
    val orderId: UUID,
    val customerId: UUID,
    val paymentId: String,
    val amount: Double,
    val status: String
)

@Component
class PaymentSqsMessenger(
    private val sqsClient: SqsClient,
    private val paymentRepository: DynamoPaymentRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${sqs.queue.create-payment:create-payment-order-queue-dev}")
    private val createPaymentQueue: String,
    @Value("\${sqs.queue.callback-payment:callback-payment-order-queue-dev}")
    private val callbackPaymentQueue: String,
    @Value("\${sqs.queue.payment-notification:payment-notification-queue-dev}")
    private val paymentNotificationQueue: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        SqsListenerHealthIndicator.registerMonitoredQueue(createPaymentQueue, 120)
    }

    private fun getQueueUrl(queueName: String): String {
        return runCatching {
            sqsClient.getQueueUrl { it.queueName(queueName) }.queueUrl()
        }.getOrElse { queueName }
    }

    /**
     * Polling da fila de ordens de pagamento vindas da OS
     */
    @Scheduled(fixedDelay = 5000)
    fun pollCreatePaymentOrders() {
        val queueUrl = getQueueUrl(createPaymentQueue)
        runCatching {
            val response = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(2)
                    .messageAttributeNames("All")
                    .build()
            )

            SqsListenerHealthIndicator.recordHeartbeat(createPaymentQueue)

            for (msg in response.messages()) {
                val event = objectMapper.readValue(msg.body(), PaymentOrderInboundEvent::class.java)
                val traceparent = msg.messageAttributes()["traceparent"]?.stringValue()

                val payment = PaymentDynamoItem(
                    orderId = event.orderId,
                    customerId = event.customerId,
                    amount = event.amount,
                    status = "PENDING",
                    originTraceId = traceparent
                )
                paymentRepository.save(payment)
                logger.info("Ordem de pagamento criada: ID={}, OS={}, Valor={}", payment.id, event.orderId, event.amount)

                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build()
                )
            }
        }.onFailure { ex ->
            logger.warn("Erro ao ler fila {}: {}", createPaymentQueue, ex.message)
        }
    }

    /**
     * Notifica OS e Notification Service que o pagamento foi confirmado (PF-10 W3C Trace)
     */
    fun notifyPaymentSuccess(payment: PaymentDynamoItem) {
        val currentSpan = Span.current()
        val traceparent = "00-${currentSpan.spanContext.traceId}-${currentSpan.spanContext.spanId}-01"

        // 1. Notifica a OS via callback-payment-order-queue
        val callbackUrl = getQueueUrl(callbackPaymentQueue)
        val callbackEvent = PaymentCompletedCallbackEvent(payment.orderId, payment.id, "APPROVED")
        sqsClient.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(callbackUrl)
                .messageBody(objectMapper.writeValueAsString(callbackEvent))
                .messageAttributes(mapOf(
                    "traceparent" to MessageAttributeValue.builder().dataType("String").stringValue(traceparent).build()
                ))
                .build()
        )
        logger.info("Notificação de pagamento enviada para a OS via {}", callbackPaymentQueue)

        // 2. Notifica o cliente via payment-notification-queue
        val notifUrl = getQueueUrl(paymentNotificationQueue)
        val notifEvent = PaymentReceiptNotificationEvent(payment.orderId, payment.customerId, payment.id, payment.amount, "APPROVED")
        sqsClient.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(notifUrl)
                .messageBody(objectMapper.writeValueAsString(notifEvent))
                .messageAttributes(mapOf(
                    "traceparent" to MessageAttributeValue.builder().dataType("String").stringValue(traceparent).build()
                ))
                .build()
        )
        logger.info("Recibo enviado para fila de notificação via {}", paymentNotificationQueue)
    }
}
