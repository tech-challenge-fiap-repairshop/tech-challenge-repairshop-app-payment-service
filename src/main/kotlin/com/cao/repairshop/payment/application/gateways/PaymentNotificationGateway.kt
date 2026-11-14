package com.cao.repairshop.payment.application.gateways

import java.util.UUID

interface PaymentNotificationGateway {
    fun sendPaymentSuccess(orderId: UUID, paymentId: String, amount: Double)
    fun sendNotification(
        eventType: String,
        orderId: UUID,
        customerId: UUID,
        paymentId: String,
        amount: Double,
        status: String,
        qrCode: String? = null,
        ticketUrl: String? = null,
        reason: String? = null
    )
}

