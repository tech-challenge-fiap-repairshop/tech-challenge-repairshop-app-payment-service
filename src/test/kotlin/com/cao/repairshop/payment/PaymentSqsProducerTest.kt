package com.cao.repairshop.payment

import com.cao.repairshop.payment.infrastructure.messaging.PaymentSqsProducer
import com.cao.repairshop.payment.infrastructure.messaging.dtos.PaymentNotificationEvent
import com.cao.repairshop.payment.infrastructure.messaging.dtos.PaymentSuccessEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.util.UUID
import java.util.function.Consumer

class PaymentSqsProducerTest {

    private val sqsClient: SqsClient = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var producer: PaymentSqsProducer

    private val notificationQueue = "payment-notification-queue"
    private val successQueue = "payment-success-queue"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { sqsClient.getQueueUrl(any<Consumer<GetQueueUrlRequest.Builder>>()) } answers {
            val fn = firstArg<Consumer<GetQueueUrlRequest.Builder>>()
            val b = GetQueueUrlRequest.builder()
            fn.accept(b)
            GetQueueUrlResponse.builder().queueUrl("https://sqs.us-east-1.amazonaws.com/123/${b.build().queueName()}").build()
        }

        producer = PaymentSqsProducer(
            sqsClient = sqsClient,
            objectMapper = objectMapper,
            paymentNotificationQueue = notificationQueue,
            paymentSuccessQueue = successQueue
        )
    }

    @Test
    fun `sendPaymentSuccessEvent should serialize event and publish message to SQS`() {
        val orderId = UUID.randomUUID()
        val paymentId = "pay-success-123"
        val amount = 350.0

        val requestSlot = slot<SendMessageRequest>()
        every { sqsClient.sendMessage(capture(requestSlot)) } returns mockk()

        producer.sendPaymentSuccessEvent(orderId, paymentId, amount)

        verify(exactly = 1) { sqsClient.sendMessage(any<SendMessageRequest>()) }
        assertTrue(requestSlot.isCaptured)
        val sentReq = requestSlot.captured
        assertTrue(sentReq.queueUrl().contains(successQueue))

        val sentEvent = objectMapper.readValue(sentReq.messageBody(), PaymentSuccessEvent::class.java)
        assertEquals(orderId, sentEvent.orderId)
        assertEquals(paymentId, sentEvent.paymentId)
        assertEquals("APPROVED", sentEvent.status)
        assertEquals(350.0, sentEvent.amount)
    }

    @Test
    fun `sendNotification should publish notification event to SQS`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val paymentId = "pay-notif-456"

        val requestSlot = slot<SendMessageRequest>()
        every { sqsClient.sendMessage(capture(requestSlot)) } returns mockk()

        producer.sendNotification(
            eventType = "PAYMENT_CREATED",
            orderId = orderId,
            customerId = customerId,
            paymentId = paymentId,
            amount = 150.0,
            status = "PENDING",
            qrCode = "pix-qr"
        )

        verify(exactly = 1) { sqsClient.sendMessage(any<SendMessageRequest>()) }
        assertTrue(requestSlot.isCaptured)
        val sentReq = requestSlot.captured
        assertTrue(sentReq.queueUrl().contains(notificationQueue))

        val sentEvent = objectMapper.readValue(sentReq.messageBody(), PaymentNotificationEvent::class.java)
        assertEquals("PAYMENT_CREATED", sentEvent.eventType)
        assertEquals(orderId, sentEvent.orderId)
        assertEquals("PENDING", sentEvent.status)
        assertEquals("pix-qr", sentEvent.qrCode)
    }
}
