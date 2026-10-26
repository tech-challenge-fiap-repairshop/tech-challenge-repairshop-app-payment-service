package br.com.fiap.repairshop.payment

import br.com.fiap.repairshop.payment.infrastructure.messaging.PaymentSqsMessenger
import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.PaymentDynamoItem
import br.com.fiap.repairshop.payment.infrastructure.web.CreatePaymentRequest
import br.com.fiap.repairshop.payment.infrastructure.web.PaymentController
import br.com.fiap.repairshop.payment.infrastructure.web.WebhookNotification
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class PaymentControllerTest {

    private val repository: DynamoPaymentRepository = mockk(relaxed = true)
    private val messenger: PaymentSqsMessenger = mockk(relaxed = true)
    private lateinit var controller: PaymentController

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        controller = PaymentController(repository, messenger)
    }

    @Test
    fun `should create payment successfully`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val req = CreatePaymentRequest(orderId = orderId, customerId = customerId, amount = 150.0)

        val slot = slot<PaymentDynamoItem>()
        every { repository.save(capture(slot)) } just Runs

        val res = controller.createPayment(req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertNotNull(res.body)
        assertEquals(orderId, res.body?.orderId)
        assertEquals(customerId, res.body?.customerId)
        assertEquals(150.0, res.body?.amount)
        assertEquals("MERCADO_PAGO_QR", res.body?.paymentMethod)
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `should return 404 when payment not found`() {
        every { repository.findById("unknown-id") } returns null

        val res = controller.getPayment("unknown-id")

        assertEquals(HttpStatus.NOT_FOUND, res.statusCode)
    }

    @Test
    fun `should return payment when found`() {
        val item = PaymentDynamoItem(
            id = "pay-123",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 250.0
        )
        every { repository.findById("pay-123") } returns item

        val res = controller.getPayment("pay-123")

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals(item, res.body)
    }

    @Test
    fun `should confirm payment successfully when payment exists`() {
        val id = "pay-confirm-1"
        val item = PaymentDynamoItem(
            id = id,
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 300.0,
            status = "PENDING"
        )
        every { repository.findById(id) } returns item
        every { repository.updateStatus(id, "APPROVED", "DISPATCHED") } just Runs
        every { messenger.notifyPaymentSuccess(any()) } just Runs

        val res = controller.confirmPayment(id)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("APPROVED", res.body?.status)
        assertEquals("DISPATCHED", res.body?.dispatchNotificateStatus)
        verify(exactly = 1) { repository.updateStatus(id, "APPROVED", "DISPATCHED") }

        val slot = slot<PaymentDynamoItem>()
        verify(exactly = 1) { messenger.notifyPaymentSuccess(capture(slot)) }
        assertEquals("APPROVED", slot.captured.status)
        assertEquals("DISPATCHED", slot.captured.dispatchNotificateStatus)
    }

    @Test
    fun `should return 404 on confirmPayment when payment does not exist`() {
        every { repository.findById("non-existent") } returns null

        val res = controller.confirmPayment("non-existent")

        assertEquals(HttpStatus.NOT_FOUND, res.statusCode)
        verify(exactly = 1) { repository.findById("non-existent") }
        verify(exactly = 0) { repository.updateStatus(any(), any(), any()) }
        verify(exactly = 0) { messenger.notifyPaymentSuccess(any()) }
    }

    @Test
    fun `should process webhook with approved status and notify success`() {
        val id = "pay-wh-approved"
        val item = PaymentDynamoItem(
            id = id,
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 500.0,
            status = "PENDING"
        )
        every { repository.findById(id) } returns item
        every { repository.updateStatus(id, "APPROVED", "DISPATCHED") } just Runs
        every { messenger.notifyPaymentSuccess(any()) } just Runs

        val notification = WebhookNotification(id = id, status = "approved")
        val res = controller.webhook(notification)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("processed", res.body?.get("status"))
        verify(exactly = 1) { repository.updateStatus(id, "APPROVED", "DISPATCHED") }

        val slot = slot<PaymentDynamoItem>()
        verify(exactly = 1) { messenger.notifyPaymentSuccess(capture(slot)) }
        assertEquals("APPROVED", slot.captured.status)
    }

    @Test
    fun `should process webhook with rejected status without notifying success`() {
        val id = "pay-wh-rejected"
        val item = PaymentDynamoItem(
            id = id,
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 500.0,
            status = "PENDING"
        )
        every { repository.findById(id) } returns item
        every { repository.updateStatus(id, "REJECTED", "DISPATCHED") } just Runs

        val notification = WebhookNotification(id = id, status = "rejected")
        val res = controller.webhook(notification)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("processed", res.body?.get("status"))
        verify(exactly = 1) { repository.updateStatus(id, "REJECTED", "DISPATCHED") }
        verify(exactly = 0) { messenger.notifyPaymentSuccess(any()) }
    }

    @Test
    fun `should return processed on webhook when payment not found`() {
        every { repository.findById("unknown-webhook") } returns null

        val notification = WebhookNotification(id = "unknown-webhook", status = "approved")
        val res = controller.webhook(notification)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("processed", res.body?.get("status"))
        verify(exactly = 1) { repository.findById("unknown-webhook") }
        verify(exactly = 0) { repository.updateStatus(any(), any(), any()) }
        verify(exactly = 0) { messenger.notifyPaymentSuccess(any()) }
    }
}
