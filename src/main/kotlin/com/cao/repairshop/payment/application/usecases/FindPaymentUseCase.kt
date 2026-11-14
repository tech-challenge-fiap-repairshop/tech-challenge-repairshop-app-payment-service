package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.domain.Payment
import java.util.UUID

interface FindPaymentUseCase {
    fun findById(id: String): Payment?
    fun findByOrderId(orderId: UUID): List<Payment>
}

