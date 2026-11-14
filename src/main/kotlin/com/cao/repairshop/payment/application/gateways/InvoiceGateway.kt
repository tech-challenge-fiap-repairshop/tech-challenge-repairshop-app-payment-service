package com.cao.repairshop.payment.application.gateways

import com.cao.repairshop.payment.domain.Invoice
import java.util.UUID

interface InvoiceGateway {
    fun save(invoice: Invoice): Invoice
    fun findByOrderId(orderId: UUID): List<Invoice>
}

