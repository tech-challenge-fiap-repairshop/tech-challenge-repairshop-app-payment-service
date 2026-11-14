package com.cao.repairshop.payment.infrastructure.controller.dtos

import java.util.UUID

data class CreatePaymentRequest(
    val orderId: UUID? = null,
    val customerId: UUID? = null,
    val amount: Double,
    val paymentMethod: String? = "MERCADO_PAGO_QR"
)

