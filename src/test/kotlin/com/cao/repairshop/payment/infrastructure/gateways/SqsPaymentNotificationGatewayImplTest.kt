package com.cao.repairshop.payment.infrastructure.gateways

import com.cao.repairshop.payment.infrastructure.messaging.PaymentSqsProducer
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SqsPaymentNotificationGatewayImplTest {

    private lateinit var producer: PaymentSqsProducer
    private lateinit var gateway: SqsPaymentNotificationGatewayImpl

    @BeforeEach
    fun setup() {
        producer = mockk(relaxed = true)
        gateway = SqsPaymentNotificationGatewayImpl(producer)
    }

    @Test
    fun `sendPaymentSuccess should delegate to producer`() {
        val orderId = UUID.randomUUID()
        gateway.sendPaymentSuccess(orderId, "P-1", 100.0)
        verify(exactly = 1) { producer.sendPaymentSuccessEvent(orderId, "P-1", 100.0) }
    }

    @Test
    fun `sendNotification should delegate to producer with all arguments`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        gateway.sendNotification(
            eventType = "ready_for_payment",
            orderId = orderId,
            customerId = customerId,
            paymentId = "P-1",
            amount = 100.0,
            status = "PENDING",
            qrCode = "qr",
            ticketUrl = "url",
            reason = "test"
        )
        verify(exactly = 1) {
            producer.sendNotification(
                eventType = "ready_for_payment",
                orderId = orderId,
                customerId = customerId,
                paymentId = "P-1",
                amount = 100.0,
                status = "PENDING",
                qrCode = "qr",
                ticketUrl = "url",
                reason = "test"
            )
        }
    }
}

