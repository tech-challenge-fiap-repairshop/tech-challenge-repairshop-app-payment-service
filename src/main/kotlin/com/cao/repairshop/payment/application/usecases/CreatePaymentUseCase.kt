package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.infrastructure.controller.dtos.CreatePaymentRequest
import java.util.UUID

interface CreatePaymentUseCase {
    fun execute(orderId: UUID, request: CreatePaymentRequest): Payment
}

