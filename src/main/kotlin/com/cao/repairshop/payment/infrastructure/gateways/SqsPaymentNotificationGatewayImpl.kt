package com.cao.repairshop.payment.infrastructure.gateways

import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.infrastructure.messaging.PaymentSqsProducer
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SqsPaymentNotificationGatewayImpl(
    private val producer: PaymentSqsProducer
) : PaymentNotificationGateway {

    override fun sendPaymentSuccess(orderId: UUID, paymentId: String, amount: Double) {
        producer.sendPaymentSuccessEvent(orderId, paymentId, amount)
    }

    override fun sendNotification(
        eventType: String,
        orderId: UUID,
        customerId: UUID,
        paymentId: String,
        amount: Double,
        status: String,
        qrCode: String?,
        ticketUrl: String?,
        reason: String?
    ) {
        producer.sendNotification(
            eventType = eventType,
            orderId = orderId,
            customerId = customerId,
            paymentId = paymentId,
            amount = amount,
            status = status,
            qrCode = qrCode,
            ticketUrl = ticketUrl,
            reason = reason
        )
    }
}

