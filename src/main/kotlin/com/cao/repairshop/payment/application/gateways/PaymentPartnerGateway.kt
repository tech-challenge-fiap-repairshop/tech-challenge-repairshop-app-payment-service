package com.cao.repairshop.payment.application.gateways

import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.PartnerPaymentResult
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.PartnerRefundResult
import java.util.UUID

interface PaymentPartnerGateway {
    fun createPayment(orderId: UUID, customerId: UUID, amount: Double, description: String? = null): PartnerPaymentResult
    fun refundPayment(paymentId: String, amount: Double, reason: String? = null): PartnerRefundResult
}

