package com.cao.repairshop.payment.infrastructure.gateways

import com.cao.repairshop.payment.domain.Invoice
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.DynamoPaymentRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class DynamoInvoiceGatewayImplTest {

    private lateinit var repository: DynamoPaymentRepository
    private lateinit var gateway: DynamoInvoiceGatewayImpl

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        gateway = DynamoInvoiceGatewayImpl(repository)
    }

    @Test
    fun `save should persist invoice and return instance`() {
        val invoice = Invoice(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            invoiceNumber = "INV-001",
            amount = 150.0
        )

        val result = gateway.save(invoice)

        assertEquals(invoice, result)
        verify(exactly = 1) { repository.saveInvoice(any()) }
    }

    @Test
    fun `findByOrderId should return empty list`() {
        val result = gateway.findByOrderId(UUID.randomUUID())
        assertTrue(result.isEmpty())
    }
}

