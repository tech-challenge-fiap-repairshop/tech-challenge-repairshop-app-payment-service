package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.application.usecases.impl.ConfirmPaymentUseCaseImpl
import com.cao.repairshop.payment.core.exception.PaymentNotFoundException
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class ConfirmPaymentUseCaseImplTest {

    private lateinit var paymentGateway: PaymentGateway
    private lateinit var notificationGateway: PaymentNotificationGateway
    private lateinit var useCase: ConfirmPaymentUseCaseImpl

    @BeforeEach
    fun setup() {
        paymentGateway = mockk(relaxed = true)
        notificationGateway = mockk(relaxed = true)
        useCase = ConfirmPaymentUseCaseImpl(paymentGateway, notificationGateway)
    }

    @Test
    fun `execute should approve pending payment and send notifications`() {
        val paymentId = "PAY-123"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val payment = Payment(
            id = paymentId,
            orderId = orderId,
            customerId = customerId,
            amount = 100.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        every { paymentGateway.findById(paymentId) } returns payment
        every { paymentGateway.updateStatus(paymentId, PaymentStatus.APPROVED, "UPDATED", "DISPATCHED") } returns payment

        val result = useCase.execute(paymentId)

        assertEquals(PaymentStatus.APPROVED, result.status)
        verify(exactly = 1) { notificationGateway.sendPaymentSuccess(orderId, paymentId, 100.0) }
        verify(exactly = 1) {
            notificationGateway.sendNotification(
                eventType = "payment_approved",
                orderId = orderId,
                customerId = customerId,
                paymentId = paymentId,
                amount = 100.0,
                status = "APPROVED"
            )
        }
    }

    @Test
    fun `execute should return directly if payment already approved (idempotency)`() {
        val paymentId = "PAY-ALREADY-APPROVED"
        val payment = Payment(
            id = paymentId,
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 250.0,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        every { paymentGateway.findById(paymentId) } returns payment

        val result = useCase.execute(paymentId)

        assertEquals(PaymentStatus.APPROVED, result.status)
        verify(exactly = 0) { paymentGateway.updateStatus(any(), any(), any(), any()) }
        verify(exactly = 0) { notificationGateway.sendPaymentSuccess(any(), any(), any()) }
    }

    @Test
    fun `execute should throw PaymentNotFoundException when payment not found`() {
        every { paymentGateway.findById("NON-EXISTENT") } returns null

        assertThrows<PaymentNotFoundException> {
            useCase.execute("NON-EXISTENT")
        }
    }
}

