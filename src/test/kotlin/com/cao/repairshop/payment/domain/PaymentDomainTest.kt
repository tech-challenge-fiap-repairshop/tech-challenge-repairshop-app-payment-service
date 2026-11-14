package com.cao.repairshop.payment.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class PaymentDomainTest {

    @Test
    fun `should create valid payment with pending status`() {
        val payment = Payment(
            id = "P1",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 100.0
        )

        assertEquals(PaymentStatus.PENDING, payment.status)
        assertEquals(PaymentMethod.MERCADO_PAGO_QR, payment.paymentMethod)
        assertTrue(payment.canBeRefunded())
    }

    @Test
    fun `should fail when amount is negative`() {
        assertThrows<IllegalArgumentException> {
            Payment(
                id = "P2",
                orderId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                amount = -10.0
            )
        }
    }

    @Test
    fun `approve should set status to APPROVED`() {
        val payment = Payment(
            id = "P3",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 50.0
        )

        payment.approve()
        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertTrue(payment.canBeRefunded())
    }

    @Test
    fun `approve should fail if payment was already refunded`() {
        val payment = Payment(
            id = "P4",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 50.0
        )
        payment.refund("Cancelamento")

        assertThrows<IllegalStateException> {
            payment.approve()
        }
    }

    @Test
    fun `reject should set status to REJECTED`() {
        val payment = Payment(
            id = "P5",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 50.0
        )

        payment.reject()
        assertEquals(PaymentStatus.REJECTED, payment.status)
        assertFalse(payment.canBeRefunded())
    }

    @Test
    fun `reject should fail if payment was already approved or refunded`() {
        val payment1 = Payment(
            id = "P6",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 50.0
        )
        payment1.approve()
        assertThrows<IllegalStateException> { payment1.reject() }

        val payment2 = Payment(
            id = "P7",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 50.0
        )
        payment2.refund("Erro")
        assertThrows<IllegalStateException> { payment2.reject() }
    }

    @Test
    fun `refund should update status, reason and date`() {
        val payment = Payment(
            id = "P8",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 80.0
        )

        payment.refund("Inviabilidade técnica")
        assertEquals(PaymentStatus.REFUNDED, payment.status)
        assertEquals("Inviabilidade técnica", payment.refundReason)
        assertNotNull(payment.refundDate)
    }

    @Test
    fun `refund should fail if already refunded or rejected`() {
        val payment = Payment(
            id = "P9",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 80.0
        )
        payment.reject()

        assertThrows<IllegalStateException> {
            payment.refund("Outro motivo")
        }
    }
}

