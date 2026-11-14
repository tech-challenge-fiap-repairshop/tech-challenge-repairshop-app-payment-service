package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.usecases.impl.FindPaymentUseCaseImpl
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class FindPaymentUseCaseImplTest {

    private lateinit var paymentGateway: PaymentGateway
    private lateinit var useCase: FindPaymentUseCaseImpl

    @BeforeEach
    fun setup() {
        paymentGateway = mockk()
        useCase = FindPaymentUseCaseImpl(paymentGateway)
    }

    @Test
    fun `findById should return payment from gateway`() {
        val payment = Payment(
            id = "P1",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 50.0,
            status = PaymentStatus.PENDING,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )
        every { paymentGateway.findById("P1") } returns payment

        val result = useCase.findById("P1")

        assertNotNull(result)
        assertEquals("P1", result?.id)
    }

    @Test
    fun `findById should return null when not found`() {
        every { paymentGateway.findById("P-NONE") } returns null

        val result = useCase.findById("P-NONE")

        assertNull(result)
    }

    @Test
    fun `findByOrderId should return list of payments from gateway`() {
        val orderId = UUID.randomUUID()
        val payment = Payment(
            id = "P2",
            orderId = orderId,
            customerId = UUID.randomUUID(),
            amount = 75.0,
            status = PaymentStatus.APPROVED,
            paymentMethod = PaymentMethod.MERCADO_PAGO_QR
        )
        every { paymentGateway.findByOrderId(orderId) } returns listOf(payment)

        val result = useCase.findByOrderId(orderId)

        assertEquals(1, result.size)
        assertEquals("P2", result.first().id)
    }
}

