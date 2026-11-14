package com.cao.repairshop.payment.application.usecases.impl

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.application.usecases.ProcessWebhookUseCase
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.controller.dtos.WebhookNotification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProcessWebhookUseCaseImpl(
    private val paymentGateway: PaymentGateway,
    private val notificationGateway: PaymentNotificationGateway
) : ProcessWebhookUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(notification: WebhookNotification): Boolean {
        logger.info("Processando webhook do Mercado Pago: id={}, status={}", notification.id, notification.status)
        val payment = paymentGateway.findById(notification.id)
        if (payment == null) {
            logger.warn("Pagamento não localizado para webhook ID: {}", notification.id)
            return false
        }

        val isApproved = notification.status.equals("approved", ignoreCase = true)
        val newStatus = if (isApproved) PaymentStatus.APPROVED else PaymentStatus.REJECTED
        val osStatus = if (isApproved) "UPDATED" else "PENDING"

        // Idempotência: Se o pagamento já se encontra no status final notificado, encerra sem duplicar eventos
        if (payment.status == newStatus) {
            logger.info("Webhook ignorado pois status atual ({}) é idêntico ao notificado (idempotência): paymentId={}", payment.status, payment.id)
            return true
        }

        paymentGateway.updateStatus(payment.id, newStatus, osStatus, "DISPATCHED")

        if (isApproved) {
            notificationGateway.sendPaymentSuccess(payment.orderId, payment.id, payment.amount)
        }

        notificationGateway.sendNotification(
            eventType = if (isApproved) "payment_approved" else "payment_refused",
            orderId = payment.orderId,
            customerId = payment.customerId,
            paymentId = payment.id,
            amount = payment.amount,
            status = newStatus.name
        )

        logger.info("Webhook processado com sucesso para pagamento {}: status={}", payment.id, newStatus)
        return true
    }
}

