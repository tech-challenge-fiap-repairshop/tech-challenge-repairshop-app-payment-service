package com.cao.repairshop.payment.domain

import java.time.Instant
import java.util.UUID

data class Invoice(
    val id: String = UUID.randomUUID().toString(),
    val orderId: UUID,
    val customerId: UUID,
    val invoiceNumber: String,
    val amount: Double,
    val emissionDate: String = Instant.now().toString(),
    val invoiceInfo: String = "Fatura emitida para OS $orderId",
    val clientDispatch: String = "PENDING"
) {
    init {
        require(amount >= 0.0) { "O valor da fatura não pode ser negativo" }
    }
}

