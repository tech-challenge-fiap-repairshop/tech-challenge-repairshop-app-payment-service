package br.com.fiap.repairshop.payment

import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoClient
import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoPaymentRequest
import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoPaymentResponse
import br.com.fiap.repairshop.payment.infrastructure.messaging.OsReadyForPaymentInboundEvent
import br.com.fiap.repairshop.payment.infrastructure.messaging.PaymentSqsMessenger
import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.models.PaymentDynamoItem
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
    private val mercadoPagoClient: MercadoPagoClient = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var messenger: PaymentSqsMessenger

    private val osReadyQueue = "os-ready-for-payment-queue"
    private val notificationQueue = "payment-notification-queue"
    private val paymentSuccessQueue = "payment-success-queue"

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
            mercadoPagoClient = mercadoPagoClient,
            objectMapper = objectMapper,
            osReadyForPaymentQueue = osReadyQueue,
            paymentNotificationQueue = notificationQueue,
            paymentSuccessQueue = paymentSuccessQueue
        )
    }

    @Test
    fun `should poll messages from os ready queue and save to repository and delete message`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val event = OsReadyForPaymentInboundEvent(orderId = orderId, customerId = customerId, amount = 175.50)
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

        val mpResponse = MercadoPagoPaymentResponse(
            paymentId = "mp-999",
            status = "PENDING",
            qrCode = "pix-code"
        )
        every { mercadoPagoClient.createPayment(any<MercadoPagoPaymentRequest>()) } returns mpResponse

        val itemSlot = slot<PaymentDynamoItem>()
        every { repository.savePayment(capture(itemSlot)) } just Runs
        every { repository.saveInvoice(any()) } just Runs

        messenger.pollOsReadyForPayment()

        verify(exactly = 1) { repository.savePayment(any()) }
        assertEquals(orderId.toString(), itemSlot.captured.serviceOrderId)
        assertEquals(customerId.toString(), itemSlot.captured.customerId)
        assertEquals(175.50, itemSlot.captured.priceValue)
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
            messenger.pollOsReadyForPayment()
        }

        verify(exactly = 0) { repository.savePayment(any()) }
    }

    @Test
    fun `should send payment success event to payment-success-queue`() {
        val orderId = UUID.randomUUID()
        val paymentId = "pay-success-1"
        val amount = 350.0

        val sentRequests = mutableListOf<SendMessageRequest>()
        every { sqsClient.sendMessage(capture(sentRequests)) } returns SendMessageResponse.builder().build()

        messenger.sendPaymentSuccessEvent(orderId, paymentId, amount)

        verify(exactly = 1) { sqsClient.sendMessage(any<SendMessageRequest>()) }
        assertEquals(1, sentRequests.size)

        val successReq = sentRequests.first()
        assertTrue(successReq.queueUrl().contains(paymentSuccessQueue))
        assertTrue(successReq.messageBody().contains("APPROVED"))
        assertTrue(successReq.messageBody().contains(paymentId))
        assertTrue(successReq.messageBody().contains("350.0"))
    }

    @Test
    fun `should send notification event to payment-notification-queue`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val paymentId = "pay-notif-1"

        val sentRequests = mutableListOf<SendMessageRequest>()
        every { sqsClient.sendMessage(capture(sentRequests)) } returns SendMessageResponse.builder().build()

        messenger.notifyPaymentStatus(
            eventType = "ready_for_payment",
            orderId = orderId,
            customerId = customerId,
            paymentId = paymentId,
            amount = 150.0,
            status = "PENDING",
            qrCode = "pix-qr"
        )

        verify(exactly = 1) { sqsClient.sendMessage(any<SendMessageRequest>()) }
        val req = sentRequests.first()
        assertTrue(req.queueUrl().contains(notificationQueue))
        assertTrue(req.messageBody().contains("ready_for_payment"))
        assertTrue(req.messageBody().contains("pix-qr"))
    }
}
