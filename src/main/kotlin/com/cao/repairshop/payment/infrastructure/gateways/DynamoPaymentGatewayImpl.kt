package com.cao.repairshop.payment.infrastructure.gateways

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.DynamoPaymentRepository
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.mapper.PaymentDynamoMapper
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DynamoPaymentGatewayImpl(
    private val repository: DynamoPaymentRepository
) : PaymentGateway {

    override fun save(payment: Payment): Payment {
        val item = PaymentDynamoMapper.toItem(payment)
        repository.savePayment(item)
        return payment
    }

    override fun findById(id: String): Payment? {
        val item = repository.findById(id) ?: return null
        return PaymentDynamoMapper.toDomain(item)
    }

    override fun findByOrderId(orderId: UUID): List<Payment> {
        val items = repository.findByServiceOrderId(orderId)
        return items.map { PaymentDynamoMapper.toDomain(it) }
    }

    override fun updateStatus(
        id: String,
        status: PaymentStatus,
        osUpdateStatus: String?,
        dispatchStatus: String?
    ): Payment? {
        val item = repository.findById(id) ?: return null
        repository.updateStatus(item.pk, item.sk, status.name, dispatchStatus, osUpdateStatus)
        val updatedItem = repository.findById(id) ?: item.copy(status = status.name)
        return PaymentDynamoMapper.toDomain(updatedItem)
    }

    override fun refund(id: String, reason: String): Payment? {
        val refundedItem = repository.refundPaymentById(id, reason) ?: return null
        return PaymentDynamoMapper.toDomain(refundedItem)
    }
}
