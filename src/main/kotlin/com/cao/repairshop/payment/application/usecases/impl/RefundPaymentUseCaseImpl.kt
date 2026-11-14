package com.cao.repairshop.payment.application.usecases.impl

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.application.gateways.PaymentPartnerGateway
import com.cao.repairshop.payment.application.usecases.RefundPaymentUseCase
import com.cao.repairshop.payment.core.exception.PaymentNotFoundException
import com.cao.repairshop.payment.domain.Payment
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RefundPaymentUseCaseImpl(
    private val paymentGateway: PaymentGateway,
    private val partnerGateway: PaymentPartnerGateway,
    private val notificationGateway: PaymentNotificationGateway
) : RefundPaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun executeById(paymentId: String, reason: String?): Payment {
        val refundReason = reason ?: "Inviabilidade técnica na execução da OS"
        logger.info("Executando estorno de pagamento por ID: paymentId={}, motivo={}", paymentId, refundReason)

        val payment = paymentGateway.findById(paymentId)
            ?: throw PaymentNotFoundException("Pagamento não encontrado para estorno: $paymentId")

        // Validação de pré-condição de domínio (Fail-Fast antes de acionar parceiro externo)
        check(payment.canBeRefunded()) { "Pagamento com status ${payment.status} não permite estorno" }

        // 1. Invoca estorno no parceiro (Mercado Pago protegido por Circuit Breaker)
        val partnerRefundResult = partnerGateway.refundPayment(payment.id, payment.amount, refundReason)

        // 2. Aplica estorno na entidade e persiste
        payment.refund(refundReason)
        val refundedPayment = paymentGateway.refund(payment.id, refundReason) ?: payment

        // 3. Notifica clientes e ecossistema da Saga
        notificationGateway.sendNotification(
            eventType = "payment_refunded",
            orderId = payment.orderId,
            customerId = payment.customerId,
            paymentId = payment.id,
            amount = payment.amount,
            status = "REFUNDED",
            reason = refundReason
        )

        logger.info("Estorno concluído: paymentId={}, refundId={}, isFallback={}",
            payment.id, partnerRefundResult.refundId, partnerRefundResult.isFallback)
        return refundedPayment
    }

    override fun executeByOrderId(orderId: UUID, reason: String?): List<Payment> {
        val refundReason = reason ?: "Inviabilidade técnica na execução da OS"
        logger.info("Executando estorno em lote para OS: orderId={}, motivo={}", orderId, refundReason)

        val payments = paymentGateway.findByOrderId(orderId)
        if (payments.isEmpty()) {
            throw PaymentNotFoundException("Nenhum pagamento encontrado para a OS: $orderId")
        }

        val refundablePayments = payments.filter { it.canBeRefunded() }
        if (refundablePayments.isEmpty()) {
            logger.info("Nenhum pagamento elegivel para estorno na OS: {}", orderId)
            return emptyList()
        }

        return refundablePayments.map { payment ->
            executeById(payment.id, refundReason)
        }
    }
}

