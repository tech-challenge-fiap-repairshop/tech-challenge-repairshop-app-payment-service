package com.cao.repairshop.payment.infrastructure.persistence.dynamo.mapper

import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.PaymentDynamoItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.UUID

class PaymentDynamoMapperTest {

    @Test
    fun `toItem should map APPROVED and PENDING statuses correctly`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()

        val approved = Payment(
            id = "PAY-1",
            orderId = orderId,
            customerId = customerId,
            amount = 250.5,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR,
            paymentInfo = "pix-code",
            refundReason = null,
            refundDate = null,
            createdAt = "2026-11-15T10:00:00Z"
        )
        val approvedItem = PaymentDynamoMapper.toItem(approved)
        assertEquals("BILLING#$orderId", approvedItem.pk)
        assertTrue(approvedItem.sk.startsWith("PAY#"))
        assertEquals("PAYMENT", approvedItem.entityType)
        assertEquals("UPDATED", approvedItem.osUpdateStatus)
        assertEquals("DISPATCHED", approvedItem.dispatchNotificateStatus)

        val pending = approved.copy(status = PaymentStatus.PENDING, refundReason = "N/A", refundDate = "2026-11-15")
        val pendingItem = PaymentDynamoMapper.toItem(pending)
        assertEquals("PENDING", pendingItem.osUpdateStatus)
        assertEquals("PENDING", pendingItem.dispatchNotificateStatus)
        assertEquals("N/A", pendingItem.refundReason)
        assertEquals("2026-11-15", pendingItem.refundDate)
    }

    @Test
    fun `toDomain from attribute map should support primary keys`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val map = mapOf(
            "payment_id" to AttributeValue.builder().s("PAY-100").build(),
            "service_order_id" to AttributeValue.builder().s(orderId.toString()).build(),
            "customer_id" to AttributeValue.builder().s(customerId.toString()).build(),
            "status" to AttributeValue.builder().s("approved").build(),
            "payment_method" to AttributeValue.builder().s("mercado_pago_qr").build(),
            "price_value" to AttributeValue.builder().n("320.0").build(),
            "payment_info" to AttributeValue.builder().s("qr-payload").build(),
            "refund_reason" to AttributeValue.builder().s("Solicitado").build(),
            "refund_date" to AttributeValue.builder().s("2026-11-15T12:00:00Z").build(),
            "payment_date" to AttributeValue.builder().s("2026-11-15T11:00:00Z").build()
        )

        val payment = PaymentDynamoMapper.toDomain(map)
        assertEquals("PAY-100", payment.id)
        assertEquals(orderId, payment.orderId)
        assertEquals(customerId, payment.customerId)
        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(PaymentMethod.MERCADO_PAGO_QR, payment.paymentMethod)
        assertEquals(320.0, payment.amount)
        assertEquals("qr-payload", payment.paymentInfo)
        assertEquals("Solicitado", payment.refundReason)
        assertEquals("2026-11-15T12:00:00Z", payment.refundDate)
        assertEquals("2026-11-15T11:00:00Z", payment.createdAt)
        assertFalse(payment.isFallback)
    }

    @Test
    fun `toDomain from attribute map should support legacy alias keys and fallback method`() {
        val orderId = UUID.randomUUID()
        val map = mapOf(
            "id" to AttributeValue.builder().s("PAY-ALT").build(),
            "order_id" to AttributeValue.builder().s(orderId.toString()).build(),
            "amount" to AttributeValue.builder().n("99.5").build(),
            "payment_method" to AttributeValue.builder().s("MERCADO_PAGO_CONTINGENCY").build()
        )

        val payment = PaymentDynamoMapper.toDomain(map)
        assertEquals("PAY-ALT", payment.id)
        assertEquals(orderId, payment.orderId)
        assertEquals(99.5, payment.amount)
        assertEquals(PaymentMethod.MERCADO_PAGO_CONTINGENCY, payment.paymentMethod)
        assertTrue(payment.isFallback)
        assertEquals(PaymentStatus.PENDING, payment.status)
    }

    @Test
    fun `toDomain from empty or invalid attribute map should apply defaults safely`() {
        val empty = PaymentDynamoMapper.toDomain(emptyMap())
        assertTrue(empty.id.isNotBlank())
        assertEquals(0.0, empty.amount)
        assertEquals(PaymentStatus.PENDING, empty.status)
        assertEquals(PaymentMethod.MERCADO_PAGO_QR, empty.paymentMethod)
        assertEquals("", empty.paymentInfo)
        assertNull(empty.refundReason)
        assertNull(empty.refundDate)

        val invalid = PaymentDynamoMapper.toDomain(
            mapOf(
                "service_order_id" to AttributeValue.builder().s("not-uuid").build(),
                "customer_id" to AttributeValue.builder().s("not-uuid").build(),
                "status" to AttributeValue.builder().s("UNKNOWN_STATUS").build(),
                "payment_method" to AttributeValue.builder().s("UNKNOWN_METHOD").build(),
                "price_value" to AttributeValue.builder().s("not-a-number").build()
            )
        )
        assertEquals(PaymentStatus.PENDING, invalid.status)
        assertEquals(PaymentMethod.MERCADO_PAGO_QR, invalid.paymentMethod)
        assertEquals(0.0, invalid.amount)
    }

    @Test
    fun `toDomain from PaymentDynamoItem should map contingency and invalid values`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val validItem = PaymentDynamoItem(
            pk = "BILLING#$orderId",
            sk = "PAY#1#PAY-200",
            paymentId = "PAY-200",
            serviceOrderId = orderId.toString(),
            customerId = customerId.toString(),
            priceValue = 180.0,
            paymentDate = "2026-11-15T10:00:00Z",
            status = "REFUNDED",
            paymentMethod = "MERCADO_PAGO_CONTINGENCY",
            paymentInfo = "fallback-qr",
            refundReason = "Cancelado",
            refundDate = "2026-11-15T11:00:00Z"
        )

        val mapped = PaymentDynamoMapper.toDomain(validItem)
        assertEquals("PAY-200", mapped.id)
        assertEquals(orderId, mapped.orderId)
        assertEquals(customerId, mapped.customerId)
        assertEquals(PaymentStatus.REFUNDED, mapped.status)
        assertEquals(PaymentMethod.MERCADO_PAGO_CONTINGENCY, mapped.paymentMethod)
        assertTrue(mapped.isFallback)
        assertEquals("Cancelado", mapped.refundReason)

        val invalidItem = validItem.copy(
            serviceOrderId = "invalid",
            customerId = "invalid",
            status = "INVALID",
            paymentMethod = "INVALID"
        )
        val fallbackMapped = PaymentDynamoMapper.toDomain(invalidItem)
        assertEquals(PaymentStatus.PENDING, fallbackMapped.status)
        assertEquals(PaymentMethod.MERCADO_PAGO_QR, fallbackMapped.paymentMethod)
        assertFalse(fallbackMapped.isFallback)
    }
}
