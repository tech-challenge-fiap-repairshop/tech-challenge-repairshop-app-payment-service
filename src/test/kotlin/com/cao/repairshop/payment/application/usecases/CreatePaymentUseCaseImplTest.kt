package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.application.gateways.InvoiceGateway
import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.gateways.PaymentNotificationGateway
import com.cao.repairshop.payment.application.gateways.PaymentPartnerGateway
import com.cao.repairshop.payment.application.usecases.impl.CreatePaymentUseCaseImpl
import com.cao.repairshop.payment.domain.Payment
import com.cao.repairshop.payment.domain.PaymentMethod
import com.cao.repairshop.payment.domain.PaymentStatus
import com.cao.repairshop.payment.infrastructure.controller.dtos.CreatePaymentRequest
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.PartnerPaymentResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class CreatePaymentUseCaseImplTest {

    private lateinit var paymentGateway: PaymentGateway
    private lateinit var invoiceGateway: InvoiceGateway
    private lateinit var partnerGateway: PaymentPartnerGateway
    private lateinit var notificationGateway: PaymentNotificationGateway
    private lateinit var useCase: CreatePaymentUseCaseImpl

    @BeforeEach
    fun setup() {
        paymentGateway = mockk(relaxed = true)
        invoiceGateway = mockk(relaxed = true)
        partnerGateway = mockk(relaxed = true)
        notificationGateway = mockk(relaxed = true)
        useCase = CreatePaymentUseCaseImpl(paymentGateway, invoiceGateway, partnerGateway, notificationGateway)
    }

    @Test
    fun `execute should create payment and invoice successfully with partner QR code`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val request = CreatePaymentRequest(
            amount = 150.0,
            customerId = customerId,
            paymentMethod = "MERCADO_PAGO_QR"
        )

        val partnerResult = PartnerPaymentResult(
            paymentId = "MP-12345",
            status = "PENDING",
            qrCode = "mock-qr-code",
            ticketUrl = "https://mercadopago.com/ticket",
            isFallback = false
        )

        every { partnerGateway.createPayment(orderId, customerId, 150.0) } returns partnerResult
        every { paymentGateway.save(any()) } answers { firstArg() }

        val result = useCase.execute(orderId, request)

        assertNotNull(result)
        assertEquals("MP-12345", result.id)
        assertEquals(orderId, result.orderId)
        assertEquals(customerId, result.customerId)
        assertEquals(150.0, result.amount)
        assertEquals(PaymentStatus.PENDING, result.status)
        assertEquals(PaymentMethod.MERCADO_PAGO_QR, result.paymentMethod)
        assertEquals("mock-qr-code", result.paymentInfo)
        assertFalse(result.isFallback)

        verify(exactly = 1) { paymentGateway.save(any()) }
        verify(exactly = 1) { invoiceGateway.save(any()) }
        verify(exactly = 1) {
            notificationGateway.sendNotification(
                eventType = "ready_for_payment",
                orderId = orderId,
                customerId = customerId,
                paymentId = "MP-12345",
                amount = 150.0,
                status = "PENDING",
                qrCode = "mock-qr-code",
                ticketUrl = "https://mercadopago.com/ticket"
            )
        }
    }

    @Test
    fun `execute should use fallback payment method when partner returns fallback`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val request = CreatePaymentRequest(
            amount = 200.0,
            customerId = customerId
        )

        val partnerResult = PartnerPaymentResult(
            paymentId = "FALLBACK-999",
            status = "PENDING",
            qrCode = "fallback-qr",
            isFallback = true
        )

        every { partnerGateway.createPayment(orderId, customerId, 200.0) } returns partnerResult
        every { paymentGateway.save(any()) } answers { firstArg() }

        val result = useCase.execute(orderId, request)

        assertEquals("FALLBACK-999", result.id)
        assertTrue(result.isFallback)
        assertEquals(PaymentMethod.MERCADO_PAGO_CONTINGENCY, result.paymentMethod)
    }
}

