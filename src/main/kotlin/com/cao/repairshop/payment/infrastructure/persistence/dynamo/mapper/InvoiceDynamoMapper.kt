package com.cao.repairshop.payment.infrastructure.persistence.dynamo.mapper

import com.cao.repairshop.payment.domain.Invoice
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.InvoiceDynamoItem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant
import java.util.UUID

object InvoiceDynamoMapper {

    fun toItem(invoice: Invoice): InvoiceDynamoItem {
        val ts = Instant.now().toEpochMilli()
        return InvoiceDynamoItem(
            pk = "BILLING#${invoice.orderId}",
            sk = "INV#$ts#${invoice.id}",
            entityType = "INVOICE",
            invoiceId = invoice.id,
            serviceOrderId = invoice.orderId.toString(),
            customerId = invoice.customerId.toString(),
            invoiceNumber = invoice.invoiceNumber,
            priceValue = invoice.amount,
            emissionDate = invoice.emissionDate,
            invoiceInfo = invoice.invoiceInfo,
            clientDispatch = invoice.clientDispatch
        )
    }

    fun toDomain(item: Map<String, AttributeValue>): Invoice {
        val invId = item["invoice_id"]?.s() ?: UUID.randomUUID().toString()
        val soIdStr = item["service_order_id"]?.s() ?: ""
        val custIdStr = item["customer_id"]?.s() ?: ""
        val orderId = runCatching { UUID.fromString(soIdStr) }.getOrDefault(UUID.randomUUID())
        val customerId = runCatching { UUID.fromString(custIdStr) }.getOrDefault(UUID.randomUUID())
        val amount = item["price_value"]?.n()?.toDouble() ?: 0.0

        return Invoice(
            id = invId,
            orderId = orderId,
            customerId = customerId,
            invoiceNumber = item["invoice_number"]?.s() ?: "INV-UNKNOWN",
            amount = amount,
            emissionDate = item["emission_date"]?.s() ?: Instant.now().toString(),
            invoiceInfo = item["invoice_info"]?.s() ?: "",
            clientDispatch = item["client_dispatch"]?.s() ?: "PENDING"
        )
    }
}

