package com.cao.repairshop.payment.infrastructure.gateways

import com.cao.repairshop.payment.application.gateways.PaymentPartnerGateway
import com.cao.repairshop.payment.infrastructure.mercadopago.MercadoPagoClient
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.MercadoPagoPaymentRequest
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.PartnerPaymentResult
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.PartnerRefundResult
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MercadoPagoGatewayImpl(
    private val client: MercadoPagoClient
) : PaymentPartnerGateway {

    override fun createPayment(
        orderId: UUID,
        customerId: UUID,
        amount: Double,
        description: String?
    ): PartnerPaymentResult {
        val request = MercadoPagoPaymentRequest(
            orderId = orderId,
            customerId = customerId,
            amount = amount,
            description = description ?: "Ordem de Servico Oficina Mecanica"
        )
        val response = client.createPayment(request)
        return PartnerPaymentResult(
            paymentId = response.paymentId,
            status = response.status,
            qrCode = response.qrCode,
            qrCodeBase64 = response.qrCodeBase64,
            ticketUrl = response.ticketUrl,
            isFallback = response.isFallback
        )
    }

    override fun refundPayment(
        paymentId: String,
        amount: Double,
        reason: String?
    ): PartnerRefundResult {
        val response = client.refundPayment(paymentId, amount, reason)
        return PartnerRefundResult(
            refundId = response.refundId,
            paymentId = response.paymentId,
            status = response.status,
            amount = response.amount,
            isFallback = response.isFallback
        )
    }
}

