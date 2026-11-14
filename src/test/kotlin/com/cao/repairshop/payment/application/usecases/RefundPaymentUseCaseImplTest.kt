package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.application.gateways.PaymentPartnerGateway
import com.cao.repairshop.payment.application.usecases.impl.RefundPaymentUseCaseImpl
import com.cao.repairshop.payment.core.exception.PaymentNotFoundException
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.PartnerRefundResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class RefundPaymentUseCaseImplTest {

    private lateinit var paymentGateway: PaymentGateway
    private lateinit var partnerGateway: PaymentPartnerGateway
    private lateinit var notificationGateway: PaymentNotificationGateway
    private lateinit var useCase: RefundPaymentUseCaseImpl

    @BeforeEach
    fun setup() {
        paymentGateway = mockk(relaxed = true)
        partnerGateway = mockk(relaxed = true)
        notificationGateway = mockk(relaxed = true)
        useCase = RefundPaymentUseCaseImpl(paymentGateway, partnerGateway, notificationGateway)
    }

    @Test
    fun `executeById should refund approved payment successfully`() {
        val paymentId = "PAY-REF-1"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val payment = Payment(
            id = paymentId,
            orderId = orderId,
            customerId = customerId,
            amount = 300.0,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        val partnerRefundResult = PartnerRefundResult(
            refundId = "REF-MP-1",
            paymentId = paymentId,
            status = "approved",
            amount = 300.0,
            isFallback = false
        )

        every { paymentGateway.findById(paymentId) } returns payment
        every { partnerGateway.refundPayment(paymentId, 300.0, any()) } returns partnerRefundResult
        every { paymentGateway.refund(paymentId, any()) } returns payment

        val result = useCase.executeById(paymentId, "Defeito de fábrica")

        assertNotNull(result)
        assertEquals(PaymentStatus.REFUNDED, result.status)
        verify(exactly = 1) { partnerGateway.refundPayment(paymentId, 300.0, "Defeito de fábrica") }
        verify(exactly = 1) { paymentGateway.refund(paymentId, "Defeito de fábrica") }
        verify(exactly = 1) {
            notificationGateway.sendNotification(
                eventType = "payment_refunded",
                orderId = orderId,
                customerId = customerId,
                paymentId = paymentId,
                amount = 300.0,
                status = "REFUNDED",
                reason = "Defeito de fábrica"
            )
        }
    }

    @Test
    fun `executeById should throw PaymentNotFoundException when payment not found`() {
        every { paymentGateway.findById("NOT-FOUND") } returns null

        assertThrows<PaymentNotFoundException> {
            useCase.executeById("NOT-FOUND", null)
        }
    }

    @Test
    fun `executeById should fail when payment is not refundable`() {
        val payment = Payment(
            id = "PAY-REJECTED",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 50.0,
            status = PaymentStatus.REJECTED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )
        every { paymentGateway.findById("PAY-REJECTED") } returns payment

        assertThrows<IllegalStateException> {
            useCase.executeById("PAY-REJECTED", null)
        }
    }

    @Test
    fun `executeByOrderId should refund all refundable payments for order`() {
        val orderId = UUID.randomUUID()
        val p1 = Payment(
            id = "P-ORD-1",
            orderId = orderId,
            customerId = UUID.randomUUID(),
            amount = 100.0,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )
        val p2 = Payment(
            id = "P-ORD-2",
            orderId = orderId,
            customerId = UUID.randomUUID(),
            amount = 50.0,
            status = PaymentStatus.REFUNDED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        every { paymentGateway.findByOrderId(orderId) } returns listOf(p1, p2)
        every { paymentGateway.findById("P-ORD-1") } returns p1
        every { partnerGateway.refundPayment("P-ORD-1", 100.0, any()) } returns PartnerRefundResult(
            refundId = "REF-1",
            paymentId = "P-ORD-1",
            status = "approved",
            amount = 100.0,
            isFallback = false
        )
        every { paymentGateway.refund("P-ORD-1", any()) } returns p1

        val results = useCase.executeByOrderId(orderId, "Cancelamento")

        assertEquals(1, results.size)
        assertEquals("P-ORD-1", results.first().id)
    }

    @Test
    fun `executeByOrderId should throw PaymentNotFoundException when no payments exist`() {
        val orderId = UUID.randomUUID()
        every { paymentGateway.findByOrderId(orderId) } returns emptyList()

        assertThrows<PaymentNotFoundException> {
            useCase.executeByOrderId(orderId, null)
        }
    }
}
