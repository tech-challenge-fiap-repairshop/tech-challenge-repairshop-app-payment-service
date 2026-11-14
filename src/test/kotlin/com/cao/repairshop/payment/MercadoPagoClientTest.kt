package com.cao.repairshop.payment

import com.cao.repairshop.payment.infrastructure.mercadopago.MercadoPagoClient
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.MercadoPagoPaymentRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class MercadoPagoClientTest {

    private lateinit var client: MercadoPagoClient

    @BeforeEach
    fun setUp() {
        client = MercadoPagoClient(accessToken = "mock-offline-token")
    }

    @Test
    fun `createPayment with sandbox token should return mock response`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val request = MercadoPagoPaymentRequest(
            orderId = orderId,
            customerId = customerId,
            amount = 150.0
        )

        val response = client.createPayment(request)

        assertNotNull(response)
        assertTrue(response.paymentId.startsWith("MP-"))
        assertEquals("PENDING", response.status)
        assertNotNull(response.qrCode)
        assertFalse(response.isFallback)
    }

    @Test
    fun `fallbackCreatePayment should return contingency approved mock response`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val request = MercadoPagoPaymentRequest(
            orderId = orderId,
            customerId = customerId,
            amount = 150.0
        )
        val throwable = RuntimeException("Mercado Pago API connection timeout")

        val fallbackResponse = client.fallbackCreatePayment(request, throwable)

        assertNotNull(fallbackResponse)
        assertTrue(fallbackResponse.paymentId.startsWith("FALLBACK-MP-"))
        assertEquals("PENDING", fallbackResponse.status)
        assertNotNull(fallbackResponse.qrCode)
        assertTrue(fallbackResponse.qrCode!!.contains(request.amount.toString()))
        assertTrue(fallbackResponse.isFallback)
    }

    @Test
    fun `refundPayment with offline token should return mock refund response`() {
        val response = client.refundPayment("MP-12345", 150.0, "Cancelamento do cliente")

        assertNotNull(response)
        assertTrue(response.refundId.startsWith("REF-MP-"))
        assertEquals("MP-12345", response.paymentId)
        assertEquals("REFUNDED", response.status)
        assertEquals(150.0, response.amount)
        assertFalse(response.isFallback)
    }

    @Test
    fun `fallbackRefundPayment should return contingency refund response`() {
        val throwable = RuntimeException("API timeout")
        val fallback = client.fallbackRefundPayment("MP-99999", 200.0, "Falha de rede", throwable)

        assertNotNull(fallback)
        assertTrue(fallback.refundId.startsWith("FALLBACK-REF-"))
        assertEquals("MP-99999", fallback.paymentId)
        assertEquals("REFUNDED", fallback.status)
        assertEquals(200.0, fallback.amount)
        assertTrue(fallback.isFallback)
    }
}
