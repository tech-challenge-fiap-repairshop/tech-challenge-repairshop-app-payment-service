package br.com.fiap.repairshop.payment

import br.com.fiap.repairshop.payment.infrastructure.messaging.PaymentSqsMessenger
import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.PaymentDynamoItem
import br.com.fiap.repairshop.payment.infrastructure.web.CreatePaymentRequest
import br.com.fiap.repairshop.payment.infrastructure.web.PaymentController
import br.com.fiap.repairshop.payment.infrastructure.web.WebhookNotification
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import java.util.UUID

class PaymentControllerTest {

    private lateinit var repository: DynamoPaymentRepository
    private lateinit var messenger: PaymentSqsMessenger
    private lateinit var controller: PaymentController

    @BeforeEach
    fun setUp() {
        repository = mock(DynamoPaymentRepository::class.java)
        messenger = mock(PaymentSqsMessenger::class.java)
        controller = PaymentController(repository, messenger)
    }

    @Test
    fun `should create payment successfully`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val req = CreatePaymentRequest(orderId = orderId, customerId = customerId, amount = 150.0)

        val res = controller.createPayment(req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertNotNull(res.body)
        assertEquals(orderId, res.body?.orderId)
        assertEquals(customerId, res.body?.customerId)
        assertEquals(150.0, res.body?.amount)
        verify(repository, times(1)).save(res.body!!)
    }

    @Test
    fun `should return 404 when payment not found`() {
        `when`(repository.findById("unknown-id")).thenReturn(null)

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
        `when`(repository.findById("pay-123")).thenReturn(item)

        val res = controller.getPayment("pay-123")

        assertEquals(HttpStatus.OK, res.statusCode)
        assertEquals("pay-123", res.body?.id)
    }

    @Test
    fun `should handle webhook and confirm payment`() {
        val orderId = UUID.randomUUID()
        val item = PaymentDynamoItem(
            id = "pay-123",
            orderId = orderId,
            customerId = UUID.randomUUID(),
            amount = 200.0,
            status = "PENDING"
        )
        `when`(repository.findById("pay-123")).thenReturn(item)

        val webhook = WebhookNotification(id = "pay-123", status = "approved", orderId = orderId)
        val res = controller.webhook(webhook)

        assertEquals(HttpStatus.OK, res.statusCode)
        verify(repository).updateStatus("pay-123", "APPROVED", "DISPATCHED")
        verify(messenger).notifyPaymentSuccess(item.copy(status = "APPROVED"))
    }
}
