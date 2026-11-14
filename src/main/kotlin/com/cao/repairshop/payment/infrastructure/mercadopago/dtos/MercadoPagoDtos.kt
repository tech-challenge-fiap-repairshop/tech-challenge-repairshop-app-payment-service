package com.cao.repairshop.payment.infrastructure.mercadopago.dtos

import java.util.UUID

data class PartnerPaymentResult(
    val paymentId: String,
    val status: String,
    val qrCode: String,
    val qrCodeBase64: String? = null,
    val ticketUrl: String? = null,
    val isFallback: Boolean = false
)

data class PartnerRefundResult(
    val refundId: String,
    val paymentId: String,
    val status: String,
    val amount: Double,
    val isFallback: Boolean = false
)

data class MercadoPagoPaymentRequest(
    val orderId: UUID,
    val customerId: UUID,
    val amount: Double,
    val description: String = "Ordem de Servico Oficina Mecanica"
)

data class MercadoPagoPaymentResponse(
    val paymentId: String,
    val status: String,
    val qrCode: String,
    val qrCodeBase64: String? = null,
    val ticketUrl: String? = null,
    val isFallback: Boolean = false
)

data class MercadoPagoRefundResponse(
    val refundId: String,
    val paymentId: String,
    val status: String,
    val amount: Double,
    val isFallback: Boolean = false
)

