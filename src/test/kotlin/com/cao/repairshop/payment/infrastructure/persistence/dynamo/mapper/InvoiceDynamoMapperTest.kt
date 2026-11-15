package com.cao.repairshop.payment.infrastructure.persistence.dynamo.mapper

import com.cao.repairshop.payment.domain.Invoice
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.UUID

class InvoiceDynamoMapperTest {

    @Test
    fun `toItem should map all invoice fields`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val invoice = Invoice(
            id = "INV-1",
            orderId = orderId,
            customerId = customerId,
            invoiceNumber = "NF-2026-001",
            amount = 450.75,
            emissionDate = "2026-11-15T12:00:00Z",
            invoiceInfo = "Detalhes NF",
            clientDispatch = "DISPATCHED"
        )

        val item = InvoiceDynamoMapper.toItem(invoice)
        assertEquals("BILLING#$orderId", item.pk)
        assertTrue(item.sk.startsWith("INV#"))
        assertEquals("INVOICE", item.entityType)
        assertEquals("INV-1", item.invoiceId)
        assertEquals(orderId.toString(), item.serviceOrderId)
        assertEquals(customerId.toString(), item.customerId)
        assertEquals("NF-2026-001", item.invoiceNumber)
        assertEquals(450.75, item.priceValue)
        assertEquals("2026-11-15T12:00:00Z", item.emissionDate)
        assertEquals("Detalhes NF", item.invoiceInfo)
        assertEquals("DISPATCHED", item.clientDispatch)
    }

    @Test
    fun `toDomain should map complete attribute map`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val map = mapOf(
            "invoice_id" to AttributeValue.builder().s("INV-55").build(),
            "service_order_id" to AttributeValue.builder().s(orderId.toString()).build(),
            "customer_id" to AttributeValue.builder().s(customerId.toString()).build(),
            "price_value" to AttributeValue.builder().n("199.9").build(),
            "invoice_number" to AttributeValue.builder().s("NF-999").build(),
            "emission_date" to AttributeValue.builder().s("2026-11-15T14:00:00Z").build(),
            "invoice_info" to AttributeValue.builder().s("PIX pago").build(),
            "client_dispatch" to AttributeValue.builder().s("SENT").build()
        )

        val invoice = InvoiceDynamoMapper.toDomain(map)
        assertEquals("INV-55", invoice.id)
        assertEquals(orderId, invoice.orderId)
        assertEquals(customerId, invoice.customerId)
        assertEquals(199.9, invoice.amount)
        assertEquals("NF-999", invoice.invoiceNumber)
        assertEquals("2026-11-15T14:00:00Z", invoice.emissionDate)
        assertEquals("PIX pago", invoice.invoiceInfo)
        assertEquals("SENT", invoice.clientDispatch)
    }

    @Test
    fun `toDomain with empty or invalid map should apply default values`() {
        val empty = InvoiceDynamoMapper.toDomain(emptyMap())
        assertTrue(empty.id.isNotBlank())
        assertEquals(0.0, empty.amount)
        assertEquals("INV-UNKNOWN", empty.invoiceNumber)
        assertEquals("", empty.invoiceInfo)
        assertEquals("PENDING", empty.clientDispatch)

        val invalid = InvoiceDynamoMapper.toDomain(
            mapOf(
                "service_order_id" to AttributeValue.builder().s("bad-uuid").build(),
                "customer_id" to AttributeValue.builder().s("bad-uuid").build(),
                "price_value" to AttributeValue.builder().s("not-number").build()
            )
        )
        assertEquals(0.0, invalid.amount)
        assertEquals("INV-UNKNOWN", invalid.invoiceNumber)
    }
}
