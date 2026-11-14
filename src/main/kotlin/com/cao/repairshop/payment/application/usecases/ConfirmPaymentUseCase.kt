package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.domain.Payment

interface ConfirmPaymentUseCase {
    fun execute(paymentId: String): Payment
}

