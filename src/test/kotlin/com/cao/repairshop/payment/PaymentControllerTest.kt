package com.cao.repairshop.payment

import com.cao.repairshop.payment.application.usecases.*
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.controller.PaymentController
import com.cao.repairshop.payment.infrastructure.controller.dtos.CreatePaymentRequest
import com.cao.repairshop.payment.infrastructure.controller.dtos.RefundPaymentRequest
import com.cao.repairshop.payment.infrastructure.controller.dtos.WebhookNotification
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class PaymentControllerTest {

    private val createPaymentUseCase: CreatePaymentUseCase = mockk()
    private val confirmPaymentUseCase: ConfirmPaymentUseCase = mockk()
    private val refundPaymentUseCase: RefundPaymentUseCase = mockk()
    private val findPaymentUseCase: FindPaymentUseCase = mockk()
    private val processWebhookUseCase: ProcessWebhookUseCase = mockk()

    private lateinit var controller: PaymentController

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        controller = PaymentController(
            createPaymentUseCase = createPaymentUseCase,
            confirmPaymentUseCase = confirmPaymentUseCase,
            refundPaymentUseCase = refundPaymentUseCase,
            findPaymentUseCase = findPaymentUseCase,
            processWebhookUseCase = processWebhookUseCase
        )
    }

    @Test
    fun `POST createPaymentForOrder should delegate to use case and return 201 Created`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val req = CreatePaymentRequest(orderId = orderId, customerId = customerId, amount = 250.0)
        val domainPayment = Payment(
            id = "pay-123",
            orderId = orderId,
            customerId = customerId,
            amount = 250.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR,
            paymentInfo = "qr-code-pix"
        )

        every { createPaymentUseCase.execute(orderId, req) } returns domainPayment

        val res = controller.createPaymentForOrder(orderId, req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertNotNull(res.body)
        assertEquals("pay-123", res.body?.paymentId)
        assertEquals(orderId.toString(), res.body?.serviceOrderId)
        assertEquals(250.0, res.body?.priceValue)
        verify(exactly = 1) { createPaymentUseCase.execute(orderId, req) }
    }

    @Test
    fun `POST createPayment should delegate to use case and return 201 Created`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val req = CreatePaymentRequest(orderId = orderId, customerId = customerId, amount = 150.0)
        val domainPayment = Payment(
            id = "pay-456",
            orderId = orderId,
            customerId = customerId,
            amount = 150.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        every { createPaymentUseCase.execute(orderId, req) } returns domainPayment

        val res = controller.createPayment(req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertEquals(orderId.toString(), res.body?.serviceOrderId)
        assertEquals(150.0, res.body?.priceValue)
        verify(exactly = 1) { createPaymentUseCase.execute(orderId, req) }
    }

    @Test
    fun `GET getPaymentById should return 404 when payment not found`() {
        every { findPaymentUseCase.findById("unknown-id") } returns null

        val res = controller.getPaymentById("unknown-id")

        assertEquals(HttpStatus.NOT_FOUND, res.statusCode)
        verify(exactly = 1) { findPaymentUseCase.findById("unknown-id") }
    }

    @Test
    fun `GET getPaymentById should return payment when found`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val domainPayment = Payment(
            id = "pay-789",
            orderId = orderId,
            customerId = customerId,
            amount = 350.0,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        every { findPaymentUseCase.findById("pay-789") } returns domainPayment

        val res = controller.getPaymentById("pay-789")

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("pay-789", res.body?.paymentId)
        assertEquals("APPROVED", res.body?.status)
    }

    @Test
    fun `GET getPaymentsByOrder should return list of payments`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val payments = listOf(
            Payment(
                id = "pay-1",
                orderId = orderId,
                customerId = customerId,
                amount = 100.0,
                status = PaymentStatus.PENDING,
                paymentMethod = PaymentMethod.MERCADO_PAGO_QR
            )
        )

        every { findPaymentUseCase.findByOrderId(orderId) } returns payments

        val res = controller.getPaymentsByOrder(orderId)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals(1, res.body?.size)
        assertEquals("pay-1", res.body?.first()?.paymentId)
    }

    @Test
    fun `POST confirmPayment should delegate to confirmPaymentUseCase and return 200 OK`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val confirmedPayment = Payment(
            id = "pay-conf",
            orderId = orderId,
            customerId = customerId,
            amount = 200.0,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        every { confirmPaymentUseCase.execute("pay-conf") } returns confirmedPayment

        val res = controller.confirmPayment("pay-conf")

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("APPROVED", res.body?.status)
        verify(exactly = 1) { confirmPaymentUseCase.execute("pay-conf") }
    }

    @Test
    fun `POST refundPayment should delegate to refundPaymentUseCase and return 200 OK`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val refundReq = RefundPaymentRequest(reason = "OS Cancelada pelo cliente")
        val refundedPayment = Payment(
            id = "pay-refund",
            orderId = orderId,
            customerId = customerId,
            amount = 200.0,
            status = PaymentStatus.REFUNDED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR,
            refundReason = "OS Cancelada pelo cliente"
        )

        every { refundPaymentUseCase.executeById("pay-refund", "OS Cancelada pelo cliente") } returns refundedPayment

        val res = controller.refundPayment("pay-refund", refundReq)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("REFUNDED", res.body?.status)
        assertEquals("OS Cancelada pelo cliente", res.body?.refundReason)
        verify(exactly = 1) { refundPaymentUseCase.executeById("pay-refund", "OS Cancelada pelo cliente") }
    }

    @Test
    fun `POST refundPaymentByOrder should delegate to refundPaymentUseCase and return 200 OK`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val refundReq = RefundPaymentRequest(reason = "Compensação de cancelamento")
        val refundedPayment = Payment(
            id = "pay-refund-order",
            orderId = orderId,
            customerId = customerId,
            amount = 150.0,
            status = PaymentStatus.REFUNDED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR,
            refundReason = "Compensação de cancelamento"
        )

        every { refundPaymentUseCase.executeByOrderId(orderId, "Compensação de cancelamento") } returns listOf(refundedPayment)

        val res = controller.refundPaymentByOrder(orderId, refundReq)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals(1, res.body?.size)
        assertEquals("REFUNDED", res.body?.first()?.status)
    }

    @Test
    fun `POST webhook should delegate to processWebhookUseCase and return 200 OK`() {
        val webhookBody = WebhookNotification(id = "mp-12345", status = "approved")

        every { processWebhookUseCase.execute(webhookBody) } returns true

        val res = controller.webhook(webhookBody)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("processed", res.body?.get("status"))
        verify(exactly = 1) { processWebhookUseCase.execute(webhookBody) }
    }
}
