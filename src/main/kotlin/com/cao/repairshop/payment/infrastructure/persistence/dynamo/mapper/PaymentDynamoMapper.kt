package com.cao.repairshop.payment.infrastructure.persistence.dynamo.mapper

import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.PaymentDynamoItem
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant
import java.util.UUID

object PaymentDynamoMapper {

    fun toItem(payment: Payment): PaymentDynamoItem {
        val ts = Instant.now().toEpochMilli()
        return PaymentDynamoItem(
            pk = "BILLING#${payment.orderId}",
            sk = "PAY#$ts#${payment.id}",
            entityType = "PAYMENT",
            paymentId = payment.id,
            serviceOrderId = payment.orderId.toString(),
            customerId = payment.customerId.toString(),
            priceValue = payment.amount,
            paymentDate = payment.createdAt,
            status = payment.status.name,
            paymentMethod = payment.paymentMethod.name,
            osUpdateStatus = if (payment.status == PaymentStatus.APPROVED) "UPDATED" else "PENDING",
            paymentInfo = payment.paymentInfo,
            dispatchNotificateStatus = if (payment.status == PaymentStatus.APPROVED) "DISPATCHED" else "PENDING",
            refundReason = payment.refundReason,
            refundDate = payment.refundDate
        )
    }

    fun toDomain(item: Map<String, AttributeValue>): Payment {
        val pid = item["payment_id"]?.s() ?: item["id"]?.s() ?: UUID.randomUUID().toString()
        val soIdStr = item["service_order_id"]?.s() ?: item["order_id"]?.s() ?: ""
        val custIdStr = item["customer_id"]?.s() ?: ""
        val orderId = runCatching { UUID.fromString(soIdStr) }.getOrDefault(UUID.randomUUID())
        val customerId = runCatching { UUID.fromString(custIdStr) }.getOrDefault(UUID.randomUUID())
        val statusStr = item["status"]?.s() ?: "PENDING"
        val methodStr = item["payment_method"]?.s() ?: "MERCADO_PAGO_QR"
        val status = runCatching { PaymentStatus.valueOf(statusStr.uppercase()) }.getOrDefault(PaymentStatus.PENDING)
        val method = runCatching { PaymentMethod.valueOf(methodStr.uppercase()) }.getOrDefault(PaymentMethod.MERCADO_PAGO_QR)
        val amount = item["price_value"]?.n()?.toDouble() ?: item["amount"]?.n()?.toDouble() ?: 0.0

        return Payment(
            id = pid,
            orderId = orderId,
            customerId = customerId,
            amount = amount,
            status = status,
            paymentMethod = method,
            paymentInfo = item["payment_info"]?.s() ?: "",
            ticketUrl = null,
            isFallback = method == PaymentMethod.MERCADO_PAGO_CONTINGENCY,
            refundReason = item["refund_reason"]?.s(),
            refundDate = item["refund_date"]?.s(),
            createdAt = item["payment_date"]?.s() ?: Instant.now().toString()
        )
    }

    fun toDomain(item: PaymentDynamoItem): Payment {
        val orderId = runCatching { UUID.fromString(item.serviceOrderId) }.getOrDefault(UUID.randomUUID())
        val customerId = runCatching { UUID.fromString(item.customerId) }.getOrDefault(UUID.randomUUID())
        val status = runCatching { PaymentStatus.valueOf(item.status.uppercase()) }.getOrDefault(PaymentStatus.PENDING)
        val method = runCatching { PaymentMethod.valueOf(item.paymentMethod.uppercase()) }.getOrDefault(PaymentMethod.MERCADO_PAGO_QR)

        return Payment(
            id = item.paymentId,
            orderId = orderId,
            customerId = customerId,
            amount = item.priceValue,
            status = status,
            paymentMethod = method,
            paymentInfo = item.paymentInfo,
            ticketUrl = null,
            isFallback = method == PaymentMethod.MERCADO_PAGO_CONTINGENCY,
            refundReason = item.refundReason,
            refundDate = item.refundDate,
            createdAt = item.paymentDate
        )
    }
}

