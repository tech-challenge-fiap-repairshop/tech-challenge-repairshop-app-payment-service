package com.cao.repairshop.payment.application.usecases.impl

import com.cao.repairshop.payment.application.gateways.InvoiceGateway
import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.application.gateways.PaymentPartnerGateway
import com.cao.repairshop.payment.application.usecases.CreatePaymentUseCase
import com.cao.repairshop.payment.domain.Invoice
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.controller.dtos.CreatePaymentRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CreatePaymentUseCaseImpl(
    private val paymentGateway: PaymentGateway,
    private val invoiceGateway: InvoiceGateway,
    private val partnerGateway: PaymentPartnerGateway,
    private val notificationGateway: PaymentNotificationGateway
) : CreatePaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(orderId: UUID, request: CreatePaymentRequest): Payment {
        val customerId = request.customerId ?: UUID.randomUUID()
        logger.info("Executando criação de pagamento: orderId={}, customerId={}, amount={}", orderId, customerId, request.amount)

        // 1. Invoca parceiro (Mercado Pago protegido por Circuit Breaker)
        val partnerResult = partnerGateway.createPayment(
            orderId = orderId,
            customerId = customerId,
            amount = request.amount
        )

        val paymentMethod = if (partnerResult.isFallback) {
            PaymentMethod.MERCADO_PAGO_CONTINGENCY
        } else {
            runCatching { PaymentMethod.valueOf(request.paymentMethod ?: "MERCADO_PAGO_QR") }
                .getOrDefault(PaymentMethod.MERCADO_PAGO_QR)
        }

        // 2. Cria e salva a entidade de domínio Payment
        val payment = Payment(
            id = partnerResult.paymentId,
            orderId = orderId,
            customerId = customerId,
            amount = request.amount,
            status = PaymentStatus.PENDING,
            paymentMethod = paymentMethod,
            paymentInfo = partnerResult.qrCode,
            ticketUrl = partnerResult.ticketUrl,
            isFallback = partnerResult.isFallback
        )
        val savedPayment = paymentGateway.save(payment)

        // 3. Cria e salva a Fatura (Invoice)
        val invoice = Invoice(
            orderId = orderId,
            customerId = customerId,
            invoiceNumber = "INV-${Instant.now().epochSecond.toString().takeLast(6)}",
            amount = request.amount
        )
        invoiceGateway.save(invoice)

        // 4. Notifica clientes via SQS
        notificationGateway.sendNotification(
            eventType = "ready_for_payment",
            orderId = orderId,
            customerId = customerId,
            paymentId = partnerResult.paymentId,
            amount = request.amount,
            status = "PENDING",
            qrCode = partnerResult.qrCode,
            ticketUrl = partnerResult.ticketUrl
        )

        logger.info("Pagamento criado com sucesso: paymentId={}, isFallback={}", savedPayment.id, savedPayment.isFallback)
        return savedPayment
    }
}

