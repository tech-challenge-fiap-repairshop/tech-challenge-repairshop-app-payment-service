package com.cao.repairshop.payment.application.gateways

import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentStatus
import java.util.UUID

interface PaymentGateway {
    fun save(payment: Payment): Payment
    fun findById(id: String): Payment?
    fun findByOrderId(orderId: UUID): List<Payment>
    fun updateStatus(id: String, status: PaymentStatus, osUpdateStatus: String? = null, dispatchStatus: String? = null): Payment?
    fun refund(id: String, reason: String): Payment?
}

