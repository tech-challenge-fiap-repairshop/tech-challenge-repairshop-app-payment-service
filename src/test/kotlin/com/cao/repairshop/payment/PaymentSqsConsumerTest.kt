package com.cao.repairshop.payment

import com.cao.repairshop.payment.application.usecases.CreatePaymentUseCase
import com.cao.repairshop.payment.core.health.SqsListenerHealthIndicator
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.controller.dtos.CreatePaymentRequest
import com.cao.repairshop.payment.infrastructure.messaging.PaymentSqsConsumer
import com.cao.repairshop.payment.infrastructure.messaging.dtos.OsReadyForPaymentInboundEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.*
import java.util.UUID
import java.util.function.Consumer

class PaymentSqsConsumerTest {

    private val sqsClient: SqsClient = mockk(relaxed = true)
    private val createPaymentUseCase: CreatePaymentUseCase = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var consumer: PaymentSqsConsumer

    private val queueName = "os-ready-for-payment-queue"

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        SqsListenerHealthIndicator.reset()

        every { sqsClient.getQueueUrl(any<Consumer<GetQueueUrlRequest.Builder>>()) } answers {
            val fn = firstArg<Consumer<GetQueueUrlRequest.Builder>>()
            val b = GetQueueUrlRequest.builder()
            fn.accept(b)
            GetQueueUrlResponse.builder().queueUrl("https://sqs.us-east-1.amazonaws.com/123/${b.build().queueName()}").build()
        }

        consumer = PaymentSqsConsumer(
            sqsClient = sqsClient,
            createPaymentUseCase = createPaymentUseCase,
            objectMapper = objectMapper,
            osReadyForPaymentQueue = queueName
        )
    }

    @Test
    fun `pollOsReadyForPayment should receive message execute usecase and delete message`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val event = OsReadyForPaymentInboundEvent(orderId = orderId, customerId = customerId, amount = 180.0)
        val body = objectMapper.writeValueAsString(event)

        val message = Message.builder()
            .body(body)
            .receiptHandle("receipt-handle-123")
            .build()

        every { sqsClient.receiveMessage(any<ReceiveMessageRequest>()) } returns ReceiveMessageResponse.builder()
            .messages(listOf(message))
            .build()

        val expectedPayment = Payment(
            id = "pay-001",
            orderId = orderId,
            customerId = customerId,
            amount = 180.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )
        every { createPaymentUseCase.execute(eq(orderId), any()) } returns expectedPayment

        consumer.pollOsReadyForPayment()

        verify(exactly = 1) {
            createPaymentUseCase.execute(
                eq(orderId),
                match<CreatePaymentRequest> {
                    it.orderId == orderId && it.customerId == customerId && it.amount == 180.0
                }
            )
        }
        verify(exactly = 1) {
            sqsClient.deleteMessage(match<DeleteMessageRequest> {
                it.receiptHandle() == "receipt-handle-123"
            })
        }
    }

    @Test
    fun `pollOsReadyForPayment should handle empty messages gracefully`() {
        every { sqsClient.receiveMessage(any<ReceiveMessageRequest>()) } returns ReceiveMessageResponse.builder()
            .messages(emptyList())
            .build()

        consumer.pollOsReadyForPayment()

        verify(exactly = 0) { createPaymentUseCase.execute(any(), any()) }
        verify(exactly = 0) { sqsClient.deleteMessage(any<DeleteMessageRequest>()) }
    }
}
