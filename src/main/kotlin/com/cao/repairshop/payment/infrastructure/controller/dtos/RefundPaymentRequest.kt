package com.cao.repairshop.payment.infrastructure.controller.dtos

data class RefundPaymentRequest(
    val reason: String? = "Inviabilidade técnica na execução da OS"
)

