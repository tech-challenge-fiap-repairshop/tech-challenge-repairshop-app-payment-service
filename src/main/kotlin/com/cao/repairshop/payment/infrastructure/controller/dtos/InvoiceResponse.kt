package com.cao.repairshop.payment.infrastructure.controller.dtos

import java.util.UUID

data class InvoiceResponse(
    val id: String,
    val orderId: UUID,
    val customerId: UUID,
    val invoiceNumber: String,
    val amount: Double,
    val emissionDate: String,
    val invoiceInfo: String
)

