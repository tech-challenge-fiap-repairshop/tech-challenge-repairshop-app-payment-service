package com.cao.repairshop.payment.infrastructure.gateways

import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.DynamoPaymentRepository
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.PaymentDynamoItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class DynamoPaymentGatewayImplTest {

    private lateinit var repository: DynamoPaymentRepository
    private lateinit var gateway: DynamoPaymentGatewayImpl

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        gateway = DynamoPaymentGatewayImpl(repository)
    }

    @Test
    fun `save should delegate to repository and return payment`() {
        val payment = Payment(
            id = "PAY-1",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 99.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )

        val result = gateway.save(payment)

        assertEquals(payment, result)
        verify(exactly = 1) { repository.savePayment(any()) }
    }

    @Test
    fun `findById should return domain payment when item exists`() {
        val paymentId = "PAY-2"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val item = PaymentDynamoItem(
            pk = "BILLING#$orderId",
            sk = "PAY#123#$paymentId",
            paymentId = paymentId,
            serviceOrderId = orderId.toString(),
            customerId = customerId.toString(),
            priceValue = 150.0,
            status = "APPROVED",
            paymentMethod = "MERCADO_PAGO_QR"
        )

        every { repository.findById(paymentId) } returns item

        val result = gateway.findById(paymentId)

        assertNotNull(result)
        assertEquals(paymentId, result?.id)
        assertEquals(PaymentStatus.APPROVED, result?.status)
    }

    @Test
    fun `findById should return null when item does not exist`() {
        every { repository.findById("NONE") } returns null

        val result = gateway.findById("NONE")

        assertNull(result)
    }

    @Test
    fun `findByOrderId should return list of mapped payments`() {
        val orderId = UUID.randomUUID()
        val item = PaymentDynamoItem(
            pk = "BILLING#$orderId",
            sk = "PAY#123#P1",
            paymentId = "P1",
            serviceOrderId = orderId.toString(),
            customerId = UUID.randomUUID().toString(),
            priceValue = 80.0,
            status = "PENDING",
            paymentMethod = "MERCADO_PAGO_QR"
        )

        every { repository.findByServiceOrderId(orderId) } returns listOf(item)

        val result = gateway.findByOrderId(orderId)

        assertEquals(1, result.size)
        assertEquals("P1", result.first().id)
    }

    @Test
    fun `updateStatus should update repository and return updated payment`() {
        val paymentId = "PAY-UP"
        val orderId = UUID.randomUUID()
        val item = PaymentDynamoItem(
            pk = "BILLING#$orderId",
            sk = "PAY#123#$paymentId",
            paymentId = paymentId,
            serviceOrderId = orderId.toString(),
            customerId = UUID.randomUUID().toString(),
            priceValue = 100.0,
            status = "PENDING",
            paymentMethod = "MERCADO_PAGO_QR"
        )
        val updatedItem = item.copy(status = "APPROVED")

        every { repository.findById(paymentId) } returns item andThen updatedItem

        val result = gateway.updateStatus(paymentId, PaymentStatus.APPROVED, "UPDATED", "DISPATCHED")

        assertNotNull(result)
        assertEquals(PaymentStatus.APPROVED, result?.status)
        verify(exactly = 1) { repository.updateStatus(item.pk, item.sk, "APPROVED", "DISPATCHED", "UPDATED") }
    }

    @Test
    fun `refund should delegate to repository and return refunded payment`() {
        val paymentId = "PAY-REF"
        val orderId = UUID.randomUUID()
        val refundedItem = PaymentDynamoItem(
            pk = "BILLING#$orderId",
            sk = "PAY#123#$paymentId",
            paymentId = paymentId,
            serviceOrderId = orderId.toString(),
            customerId = UUID.randomUUID().toString(),
            priceValue = 100.0,
            status = "REFUNDED",
            paymentMethod = "MERCADO_PAGO_QR"
        )

        every { repository.refundPaymentById(paymentId, "Defeito") } returns refundedItem

        val result = gateway.refund(paymentId, "Defeito")

        assertNotNull(result)
        assertEquals(PaymentStatus.REFUNDED, result?.status)
    }
}
