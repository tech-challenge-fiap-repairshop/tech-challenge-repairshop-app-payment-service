package br.com.fiap.repairshop.payment

import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoClient
import br.com.fiap.repairshop.payment.infrastructure.mercadopago.MercadoPagoPaymentRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class MercadoPagoClientTest {

    private lateinit var client: MercadoPagoClient

    @BeforeEach
    fun setUp() {
        client = MercadoPagoClient(accessToken = "TEST-ACCESS-TOKEN")
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
}
