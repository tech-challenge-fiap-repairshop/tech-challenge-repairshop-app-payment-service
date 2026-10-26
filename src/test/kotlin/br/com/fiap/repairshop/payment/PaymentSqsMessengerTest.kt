package br.com.fiap.repairshop.payment

import br.com.fiap.repairshop.payment.infrastructure.messaging.PaymentOrderInboundEvent
import br.com.fiap.repairshop.payment.infrastructure.messaging.PaymentSqsMessenger
import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.PaymentDynamoItem
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.*
import java.util.UUID
import java.util.function.Consumer

class PaymentSqsMessengerTest {

    private val sqsClient: SqsClient = mockk(relaxed = true)
    private val repository: DynamoPaymentRepository = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var messenger: PaymentSqsMessenger

    private val createQueue = "create-payment-queue"
    private val callbackQueue = "callback-payment-queue"
    private val notificationQueue = "notification-queue"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { sqsClient.getQueueUrl(any<Consumer<GetQueueUrlRequest.Builder>>()) } answers {
            val consumer = firstArg<Consumer<GetQueueUrlRequest.Builder>>()
            val builder = GetQueueUrlRequest.builder()
            consumer.accept(builder)
            val req = builder.build()
            GetQueueUrlResponse.builder().queueUrl("https://sqs.us-east-1.amazonaws.com/123/${req.queueName()}").build()
        }

        messenger = PaymentSqsMessenger(
            sqsClient = sqsClient,
            paymentRepository = repository,
            objectMapper = objectMapper,
            createPaymentQueue = createQueue,
            callbackPaymentQueue = callbackQueue,
            paymentNotificationQueue = notificationQueue
        )
    }

    @Test
    fun `should poll messages from create payment queue and save to repository and delete message`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val event = PaymentOrderInboundEvent(orderId = orderId, customerId = customerId, amount = 175.50)
        val jsonBody = objectMapper.writeValueAsString(event)

        val message = Message.builder()
            .body(jsonBody)
            .receiptHandle("handle-xyz")
            .messageAttributes(mapOf(
                "traceparent" to MessageAttributeValue.builder().dataType("String").stringValue("00-trace-test-01").build()
            ))
            .build()

        val receiveResponse = ReceiveMessageResponse.builder()
            .messages(listOf(message))
            .build()

        every { sqsClient.receiveMessage(any<ReceiveMessageRequest>()) } returns receiveResponse
        every { sqsClient.deleteMessage(any<DeleteMessageRequest>()) } returns DeleteMessageResponse.builder().build()

        val itemSlot = slot<PaymentDynamoItem>()
        every { repository.save(capture(itemSlot)) } just Runs

        messenger.pollCreatePaymentOrders()

        verify(exactly = 1) { repository.save(any()) }
        assertEquals(orderId, itemSlot.captured.orderId)
        assertEquals(customerId, itemSlot.captured.customerId)
        assertEquals(175.50, itemSlot.captured.amount)
        assertEquals("PENDING", itemSlot.captured.status)
        assertEquals("00-trace-test-01", itemSlot.captured.originTraceId)

        val deleteSlot = slot<DeleteMessageRequest>()
        verify(exactly = 1) { sqsClient.deleteMessage(capture(deleteSlot)) }
        assertEquals("handle-xyz", deleteSlot.captured.receiptHandle())
    }

    @Test
    fun `should handle error during polling gracefully`() {
        every { sqsClient.receiveMessage(any<ReceiveMessageRequest>()) } throws SqsException.builder().message("SQS unavailable").build()

        assertDoesNotThrow {
            messenger.pollCreatePaymentOrders()
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `should notify payment success to OS callback and notification queues`() {
        val payment = PaymentDynamoItem(
            id = "pay-success-1",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 350.0,
            status = "APPROVED"
        )

        val sentRequests = mutableListOf<SendMessageRequest>()
        every { sqsClient.sendMessage(capture(sentRequests)) } returns SendMessageResponse.builder().build()

        messenger.notifyPaymentSuccess(payment)

        verify(exactly = 2) { sqsClient.sendMessage(any<SendMessageRequest>()) }
        assertEquals(2, sentRequests.size)

        val callbackReq = sentRequests.find { it.queueUrl().contains(callbackQueue) }
        assertNotNull(callbackReq)
        assertTrue(callbackReq!!.messageBody().contains("APPROVED"))
        assertTrue(callbackReq.messageBody().contains(payment.id))
        assertTrue(callbackReq.messageAttributes().containsKey("traceparent"))

        val notifReq = sentRequests.find { it.queueUrl().contains(notificationQueue) }
        assertNotNull(notifReq)
        assertTrue(notifReq!!.messageBody().contains("APPROVED"))
        assertTrue(notifReq.messageBody().contains(payment.amount.toString()))
        assertTrue(notifReq.messageAttributes().containsKey("traceparent"))
    }
}
