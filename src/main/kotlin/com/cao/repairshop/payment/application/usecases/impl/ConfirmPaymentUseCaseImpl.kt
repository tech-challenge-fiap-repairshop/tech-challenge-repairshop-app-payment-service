package com.cao.repairshop.payment.application.usecases.impl

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.application.usecases.ConfirmPaymentUseCase
import com.cao.repairshop.payment.core.exception.PaymentNotFoundException
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ConfirmPaymentUseCaseImpl(
    private val paymentGateway: PaymentGateway,
    private val notificationGateway: PaymentNotificationGateway
) : ConfirmPaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(paymentId: String): Payment {
        logger.info("Executando confirmação de pagamento: paymentId={}", paymentId)
        val payment = paymentGateway.findById(paymentId)
            ?: throw PaymentNotFoundException("Pagamento não encontrado: $paymentId")

        // Idempotência: Se o pagamento já foi aprovado anteriormente, retorna sem duplicar eventos
        if (payment.status == PaymentStatus.APPROVED) {
            logger.info("Pagamento já se encontra aprovado (idempotência): paymentId={}", payment.id)
            return payment
        }

        payment.approve()
        val updated = paymentGateway.updateStatus(payment.id, PaymentStatus.APPROVED, "UPDATED", "DISPATCHED")
            ?: payment

        // Dispara evento de sucesso e notificação aos clientes
        notificationGateway.sendPaymentSuccess(payment.orderId, payment.id, payment.amount)
        notificationGateway.sendNotification(
            eventType = "payment_approved",
            orderId = payment.orderId,
            customerId = payment.customerId,
            paymentId = payment.id,
            amount = payment.amount,
            status = "APPROVED"
        )

        logger.info("Pagamento confirmado e aprovado com sucesso: paymentId={}", payment.id)
        return updated
    }
}

