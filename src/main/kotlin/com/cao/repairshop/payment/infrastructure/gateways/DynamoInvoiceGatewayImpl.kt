package com.cao.repairshop.payment.infrastructure.gateways

import com.cao.repairshop.payment.application.gateways.InvoiceGateway
import com.cao.repairshop.payment.domain.Invoice
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.DynamoPaymentRepository
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.mapper.InvoiceDynamoMapper
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DynamoInvoiceGatewayImpl(
    private val repository: DynamoPaymentRepository
) : InvoiceGateway {

    override fun save(invoice: Invoice): Invoice {
        val item = InvoiceDynamoMapper.toItem(invoice)
        repository.saveInvoice(item)
        return invoice
    }

    override fun findByOrderId(orderId: UUID): List<Invoice> {
        return emptyList()
    }
}

