package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.application.usecases.impl.ProcessWebhookUseCaseImpl
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.controller.dtos.WebhookNotification
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class ProcessWebhookUseCaseImplTest {

    private lateinit var paymentGateway: PaymentGateway
    private lateinit var notificationGateway: PaymentNotificationGateway
    private lateinit var useCase: ProcessWebhookUseCaseImpl

    @BeforeEach
    fun setup() {
        paymentGateway = mockk(relaxed = true)
        notificationGateway = mockk(relaxed = true)
        useCase = ProcessWebhookUseCaseImpl(paymentGateway, notificationGateway)
    }

    @Test
    fun `execute should process approved notification and update status`() {
        val paymentId = "WH-1"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val payment = Payment(
            id = paymentId,
            orderId = orderId,
            customerId = customerId,
            amount = 180.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )
        val notification = WebhookNotification(id = paymentId, status = "approved")

        every { paymentGateway.findById(paymentId) } returns payment

        val result = useCase.execute(notification)

        assertTrue(result)
        verify(exactly = 1) { paymentGateway.updateStatus(paymentId, PaymentStatus.APPROVED, "UPDATED", "DISPATCHED") }
        verify(exactly = 1) { notificationGateway.sendPaymentSuccess(orderId, paymentId, 180.0) }
        verify(exactly = 1) {
            notificationGateway.sendNotification(
                eventType = "payment_approved",
                orderId = orderId,
                customerId = customerId,
                paymentId = paymentId,
                amount = 180.0,
                status = "APPROVED"
            )
        }
    }

    @Test
    fun `execute should process rejected notification and update status`() {
        val paymentId = "WH-2"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val payment = Payment(
            id = paymentId,
            orderId = orderId,
            customerId = customerId,
            amount = 120.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )
        val notification = WebhookNotification(id = paymentId, status = "rejected")

        every { paymentGateway.findById(paymentId) } returns payment

        val result = useCase.execute(notification)

        assertTrue(result)
        verify(exactly = 1) { paymentGateway.updateStatus(paymentId, PaymentStatus.REJECTED, "PENDING", "DISPATCHED") }
        verify(exactly = 0) { notificationGateway.sendPaymentSuccess(any(), any(), any()) }
        verify(exactly = 1) {
            notificationGateway.sendNotification(
                eventType = "payment_refused",
                orderId = orderId,
                customerId = customerId,
                paymentId = paymentId,
                amount = 120.0,
                status = "REJECTED"
            )
        }
    }

    @Test
    fun `execute should return false when payment not found`() {
        every { paymentGateway.findById("WH-NONE") } returns null

        val result = useCase.execute(WebhookNotification(id = "WH-NONE", status = "approved"))

        assertFalse(result)
    }

    @Test
    fun `execute should be idempotent when payment already has notified status`() {
        val paymentId = "WH-IDEMPOTENT"
        val payment = Payment(
            id = paymentId,
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 99.0,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )
        every { paymentGateway.findById(paymentId) } returns payment

        val result = useCase.execute(WebhookNotification(id = paymentId, status = "approved"))

        assertTrue(result)
        verify(exactly = 0) { paymentGateway.updateStatus(any(), any(), any(), any()) }
        verify(exactly = 0) { notificationGateway.sendPaymentSuccess(any(), any(), any()) }
    }
}

