package com.cao.repairshop.payment.infrastructure.messaging.dtos

import java.util.UUID

data class OsReadyForPaymentInboundEvent(
    val orderId: UUID,
    val customerId: UUID,
    val amount: Double
)

data class PaymentSuccessEvent(
    val orderId: UUID,
    val paymentId: String,
    val status: String,
    val amount: Double
)

data class PaymentNotificationEvent(
    val eventType: String,
    val orderId: UUID,
    val customerId: UUID,
    val paymentId: String,
    val amount: Double,
    val status: String,
    val qrCode: String? = null,
    val ticketUrl: String? = null,
    val reason: String? = null
)

