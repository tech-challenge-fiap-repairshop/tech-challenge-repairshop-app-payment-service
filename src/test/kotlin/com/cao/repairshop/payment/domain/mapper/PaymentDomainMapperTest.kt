package com.cao.repairshop.payment.domain.mapper

import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.PaymentDynamoItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class PaymentDomainMapperTest {

    @Test
    fun `toResponse should correctly convert domain payment to dto`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val payment = Payment(
            id = "PAY-RESP-1",
            orderId = orderId,
            customerId = customerId,
            amount = 175.50,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR,
            paymentInfo = "qr-info",
            ticketUrl = "https://ticket.com",
            isFallback = false
        )

        val response = payment.toResponse()

        assertEquals("PAY-RESP-1", response.id)
        assertEquals(orderId, response.orderId)
        assertEquals(customerId, response.customerId)
        assertEquals(175.50, response.amount)
        assertEquals("APPROVED", response.status)
        assertEquals("MERCADO_PAGO_QR", response.paymentMethod)
        assertEquals("qr-info", response.paymentInfo)
        assertEquals("https://ticket.com", response.ticketUrl)
        assertFalse(response.isFallback)
    }

    @Test
    fun `toDynamoItem and toDomain should roundtrip correctly`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val payment = Payment(
            id = "PAY-DYN-1",
            orderId = orderId,
            customerId = customerId,
            amount = 299.99,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        val item = payment.toDynamoItem()

        assertEquals("BILLING#$orderId", item.pk)
        assertTrue(item.sk.startsWith("PAY#"))
        assertEquals("PAY-DYN-1", item.paymentId)
        assertEquals(299.99, item.priceValue)
        assertEquals("APPROVED", item.status)

        val domain = item.toDomain()

        assertEquals(payment.id, domain.id)
        assertEquals(payment.orderId, domain.orderId)
        assertEquals(payment.customerId, domain.customerId)
        assertEquals(payment.amount, domain.amount)
        assertEquals(payment.status, domain.status)
    }

    @Test
    fun `toDomain should handle fallback on invalid fields gracefully`() {
        val item = PaymentDynamoItem(
            paymentId = "P-FALLBACK",
            serviceOrderId = "invalid-uuid",
            customerId = "invalid-uuid",
            priceValue = 50.0,
            status = "UNKNOWN_STATUS"
        )

        val domain = item.toDomain()

        assertEquals("P-FALLBACK", domain.id)
        assertNotNull(domain.orderId)
        assertNotNull(domain.customerId)
        assertEquals(PaymentStatus.PENDING, domain.status)
    }
}

