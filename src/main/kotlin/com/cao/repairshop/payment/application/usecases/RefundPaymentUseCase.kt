package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.domain.Payment
import java.util.UUID

interface RefundPaymentUseCase {
    fun executeById(paymentId: String, reason: String?): Payment
    fun executeByOrderId(orderId: UUID, reason: String?): List<Payment>
}

