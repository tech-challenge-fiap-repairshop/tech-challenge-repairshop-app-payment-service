package br.com.fiap.repairshop.payment

import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoClient
import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoPaymentRequest
import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoPaymentResponse
import br.com.fiap.repairshop.payment.infrastructure.messaging.PaymentSqsMessenger
import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.models.PaymentDynamoItem
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
    private val mercadoPagoClient: MercadoPagoClient = mockk(relaxed = true)
    private lateinit var controller: PaymentController

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        controller = PaymentController(repository, mercadoPagoClient, messenger)
    }

    @Test
    fun `POST payment serviceOrderId should invoke Mercado Pago and save payment and invoice`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val req = CreatePaymentRequest(orderId = orderId, customerId = customerId, amount = 250.0)

        val mpResponse = MercadoPagoPaymentResponse(
            paymentId = "mp-pay-999",
            status = "pending",
            qrCode = "qr-code-pix-payload"
        )
        every { mercadoPagoClient.createPayment(any<MercadoPagoPaymentRequest>()) } returns mpResponse
        every { repository.savePayment(any()) } just Runs
        every { repository.saveInvoice(any()) } just Runs

        val res = controller.createPaymentForOrder(orderId, req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertNotNull(res.body)
        assertEquals("mp-pay-999", res.body?.paymentId)
        assertEquals(orderId.toString(), res.body?.serviceOrderId)
        assertEquals(250.0, res.body?.priceValue)
        verify(exactly = 1) { mercadoPagoClient.createPayment(any()) }
        verify(exactly = 1) { repository.savePayment(any()) }
        verify(exactly = 1) { repository.saveInvoice(any()) }
        verify(exactly = 1) { messenger.notifyPaymentStatus(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should create payment via alias POST payments successfully`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val req = CreatePaymentRequest(orderId = orderId, customerId = customerId, amount = 150.0)

        val mpResponse = MercadoPagoPaymentResponse(
            paymentId = "mp-pay-150",
            status = "pending",
            qrCode = "qr-pix"
        )
        every { mercadoPagoClient.createPayment(any<MercadoPagoPaymentRequest>()) } returns mpResponse
        every { repository.savePayment(any()) } just Runs
        every { repository.saveInvoice(any()) } just Runs

        val res = controller.createPayment(req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertNotNull(res.body)
        assertEquals(orderId.toString(), res.body?.serviceOrderId)
        assertEquals(150.0, res.body?.priceValue)
    }

    @Test
    fun `should return 404 when payment not found`() {
        every { repository.findById("unknown-id") } returns null

        val res = controller.getPaymentById("unknown-id")

        assertEquals(HttpStatus.NOT_FOUND, res.statusCode)
    }

    @Test
    fun `should return payment when found`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val item = PaymentDynamoItem(
            paymentId = "pay-123",
            serviceOrderId = orderId.toString(),
            customerId = customerId.toString(),
            priceValue = 250.0
        )
        every { repository.findById("pay-123") } returns item

        val res = controller.getPaymentById("pay-123")

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals(item, res.body)
    }

    @Test
    fun `should confirm payment successfully when payment exists`() {
        val id = "pay-confirm-1"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val item = PaymentDynamoItem(
            pk = "BILLING#$orderId",
            sk = "PAY#123#$id",
            paymentId = id,
            serviceOrderId = orderId.toString(),
            customerId = customerId.toString(),
            priceValue = 300.0,
            status = "PENDING"
        )
        every { repository.findById(id) } returns item
        every { repository.updateStatus(item.pk, item.sk, "APPROVED", "DISPATCHED", "UPDATED") } just Runs
        every { messenger.sendPaymentSuccessEvent(orderId, id, 300.0) } just Runs

        val res = controller.confirmPayment(id)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("APPROVED", res.body?.status)
        assertEquals("DISPATCHED", res.body?.dispatchNotificateStatus)
        verify(exactly = 1) { repository.updateStatus(item.pk, item.sk, "APPROVED", "DISPATCHED", "UPDATED") }
        verify(exactly = 1) { messenger.sendPaymentSuccessEvent(orderId, id, 300.0) }
    }

    @Test
    fun `should return 404 on confirmPayment when payment does not exist`() {
        every { repository.findById("non-existent") } returns null

        val res = controller.confirmPayment("non-existent")

        assertEquals(HttpStatus.NOT_FOUND, res.statusCode)
        verify(exactly = 1) { repository.findById("non-existent") }
        verify(exactly = 0) { repository.updateStatus(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { messenger.sendPaymentSuccessEvent(any(), any(), any()) }
    }

    @Test
    fun `should process webhook with approved status and notify success`() {
        val id = "pay-wh-approved"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val item = PaymentDynamoItem(
            pk = "BILLING#$orderId",
            sk = "PAY#123#$id",
            paymentId = id,
            serviceOrderId = orderId.toString(),
            customerId = customerId.toString(),
            priceValue = 500.0,
            status = "PENDING"
        )
        every { repository.findById(id) } returns item
        every { repository.updateStatus(item.pk, item.sk, "APPROVED", "DISPATCHED", "UPDATED") } just Runs
        every { messenger.sendPaymentSuccessEvent(orderId, id, 500.0) } just Runs

        val notification = WebhookNotification(id = id, status = "approved")
        val res = controller.webhook(notification)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("processed", res.body?.get("status"))
        verify(exactly = 1) { repository.updateStatus(item.pk, item.sk, "APPROVED", "DISPATCHED", "UPDATED") }
        verify(exactly = 1) { messenger.sendPaymentSuccessEvent(orderId, id, 500.0) }
    }

    @Test
    fun `should process webhook with rejected status without notifying success`() {
        val id = "pay-wh-rejected"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val item = PaymentDynamoItem(
            pk = "BILLING#$orderId",
            sk = "PAY#123#$id",
            paymentId = id,
            serviceOrderId = orderId.toString(),
            customerId = customerId.toString(),
            priceValue = 500.0,
            status = "PENDING"
        )
        every { repository.findById(id) } returns item
        every { repository.updateStatus(item.pk, item.sk, "REJECTED", "DISPATCHED", "PENDING") } just Runs

        val notification = WebhookNotification(id = id, status = "rejected")
        val res = controller.webhook(notification)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("processed", res.body?.get("status"))
        verify(exactly = 1) { repository.updateStatus(item.pk, item.sk, "REJECTED", "DISPATCHED", "PENDING") }
        verify(exactly = 0) { messenger.sendPaymentSuccessEvent(any(), any(), any()) }
    }

    @Test
    fun `should return processed on webhook when payment not found`() {
        every { repository.findById("unknown-webhook") } returns null

        val notification = WebhookNotification(id = "unknown-webhook", status = "approved")
        val res = controller.webhook(notification)

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("processed", res.body?.get("status"))
        verify(exactly = 1) { repository.findById("unknown-webhook") }
        verify(exactly = 0) { repository.updateStatus(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { messenger.sendPaymentSuccessEvent(any(), any(), any()) }
    }
}
