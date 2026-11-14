package com.cao.repairshop.payment.infrastructure.messaging

import com.cao.repairshop.payment.infrastructure.messaging.dtos.PaymentNotificationEvent
import com.cao.repairshop.payment.infrastructure.messaging.dtos.PaymentSuccessEvent
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.util.UUID

@Component
class PaymentSqsProducer(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    @Value("\${sqs.queue.payment-notification:payment-notification-queue-dev}")
    private val paymentNotificationQueue: String,
    @Value("\${sqs.queue.payment-success:payment-success-queue-dev}")
    private val paymentSuccessQueue: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private fun getQueueUrl(queueName: String): String {
        return runCatching {
            sqsClient.getQueueUrl { it.queueName(queueName) }.queueUrl()
        }.getOrElse { queueName }
    }

    fun sendPaymentSuccessEvent(orderId: UUID, paymentId: String, amount: Double) {
        val event = PaymentSuccessEvent(
            orderId = orderId,
            paymentId = paymentId,
            status = "APPROVED",
            amount = amount
        )
        val body = objectMapper.writeValueAsString(event)
        val queueUrl = getQueueUrl(paymentSuccessQueue)

        val spanContext = Span.current().spanContext
        val msgAttributes = mutableMapOf<String, MessageAttributeValue>()
        if (spanContext.isValid) {
            msgAttributes["traceparent"] = MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("00-${spanContext.traceId}-${spanContext.spanId}-${spanContext.traceFlags.asHex()}")
                .build()
        }

        val request = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(body)
            .messageAttributes(msgAttributes)
            .build()

        sqsClient.sendMessage(request)
        logger.info("Publicado evento de pagamento aprovado para OS {} na fila {}", orderId, paymentSuccessQueue)
    }

    fun sendNotification(
        eventType: String,
        orderId: UUID,
        customerId: UUID,
        paymentId: String,
        amount: Double,
        status: String,
        qrCode: String? = null,
        ticketUrl: String? = null,
        reason: String? = null
    ) {
        val event = PaymentNotificationEvent(
            eventType = eventType,
            orderId = orderId,
            customerId = customerId,
            paymentId = paymentId,
            amount = amount,
            status = status,
            qrCode = qrCode,
            ticketUrl = ticketUrl,
            reason = reason
        )
        val body = objectMapper.writeValueAsString(event)
        val queueUrl = getQueueUrl(paymentNotificationQueue)

        val request = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(body)
            .build()

        sqsClient.sendMessage(request)
        logger.info("Publicado evento {} para OS {} em {}", eventType, orderId, paymentNotificationQueue)
    }
}

