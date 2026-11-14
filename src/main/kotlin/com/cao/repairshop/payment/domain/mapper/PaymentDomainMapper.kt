package com.cao.repairshop.payment.domain.mapper

import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.controller.dtos.PaymentResponse
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.PaymentDynamoItem
import java.time.Instant
import java.util.UUID

fun Payment.toResponse(): PaymentResponse = PaymentResponse(
    id = id,
    orderId = orderId,
    customerId = customerId,
    amount = amount,
    status = status.name,
    paymentMethod = paymentMethod.name,
    paymentInfo = paymentInfo,
    ticketUrl = ticketUrl,
    isFallback = isFallback,
    refundReason = refundReason,
    refundDate = refundDate,
    createdAt = createdAt
)

fun Payment.toDynamoItem(): PaymentDynamoItem {
    val ts = Instant.now().toEpochMilli()
    return PaymentDynamoItem(
        pk = "BILLING#$orderId",
        sk = "PAY#$ts#$id",
        entityType = "PAYMENT",
        paymentId = id,
        serviceOrderId = orderId.toString(),
        customerId = customerId.toString(),
        priceValue = amount,
        paymentDate = createdAt,
        status = status.name,
        paymentMethod = paymentMethod.name,
        osUpdateStatus = if (status == PaymentStatus.APPROVED) "UPDATED" else "PENDING",
        paymentInfo = paymentInfo,
        dispatchNotificateStatus = if (status == PaymentStatus.APPROVED) "DISPATCHED" else "PENDING",
        refundReason = refundReason,
        refundDate = refundDate
    )
}

fun PaymentDynamoItem.toDomain(): Payment {
    val oId = runCatching { UUID.fromString(serviceOrderId) }.getOrDefault(UUID.randomUUID())
    val cId = runCatching { UUID.fromString(customerId) }.getOrDefault(UUID.randomUUID())
    val pStatus = runCatching { PaymentStatus.valueOf(status.uppercase()) }.getOrDefault(PaymentStatus.PENDING)
    val pMethod = runCatching { PaymentMethod.valueOf(paymentMethod.uppercase()) }.getOrDefault(PaymentMethod.MERCADO_PAGO_QR)

    return Payment(
        id = paymentId.ifBlank { UUID.randomUUID().toString() },
        orderId = oId,
        customerId = cId,
        amount = priceValue,
        status = pStatus,
        paymentMethod = pMethod,
        paymentInfo = paymentInfo,
        ticketUrl = null,
        isFallback = pMethod == PaymentMethod.MERCADO_PAGO_CONTINGENCY,
        refundReason = refundReason,
        refundDate = refundDate,
        createdAt = paymentDate
    )
}
