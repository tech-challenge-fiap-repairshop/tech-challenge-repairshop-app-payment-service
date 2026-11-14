package com.cao.repairshop.payment.domain

import java.time.Instant
import java.util.UUID

data class Payment(
    val id: String,
    val orderId: UUID,
    val customerId: UUID,
    val amount: Double,
    var status: PaymentStatus = PaymentStatus.PENDING,
    var paymentMethod: PaymentMethod = PaymentMethod.MERCADO_PAGO_QR,
    var paymentInfo: String = "",
    val ticketUrl: String? = null,
    val isFallback: Boolean = false,
    var refundReason: String? = null,
    var refundDate: String? = null,
    val createdAt: String = Instant.now().toString()
) {
    init {
        require(amount >= 0.0) { "O valor do pagamento não pode ser negativo" }
    }

    fun approve() {
        check(status != PaymentStatus.REFUNDED) { "Pagamento estornado não pode ser aprovado" }
        status = PaymentStatus.APPROVED
    }

    fun reject() {
        check(status != PaymentStatus.APPROVED) { "Pagamento já aprovado não pode ser rejeitado" }
        check(status != PaymentStatus.REFUNDED) { "Pagamento estornado não pode ser rejeitado" }
        status = PaymentStatus.REJECTED
    }

    fun canBeRefunded(): Boolean = status == PaymentStatus.APPROVED || status == PaymentStatus.PENDING

    fun refund(reason: String) {
        check(canBeRefunded()) { "Pagamento com status $status não permite estorno" }
        status = PaymentStatus.REFUNDED
        refundReason = reason
        refundDate = Instant.now().toString()
    }
}

