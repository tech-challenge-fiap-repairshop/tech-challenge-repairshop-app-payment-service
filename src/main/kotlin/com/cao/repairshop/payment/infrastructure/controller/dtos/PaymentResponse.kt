package com.cao.repairshop.payment.infrastructure.controller.dtos

import java.util.UUID

data class PaymentResponse(
    val id: String,
    val orderId: UUID,
    val customerId: UUID,
    val amount: Double,
    val status: String,
    val paymentMethod: String,
    val paymentInfo: String = "",
    val ticketUrl: String? = null,
    val isFallback: Boolean = false,
    val refundReason: String? = null,
    val refundDate: String? = null,
    val createdAt: String
) {
    val paymentId: String get() = id
    val priceValue: Double get() = amount
    val serviceOrderId: String get() = orderId.toString()
}
