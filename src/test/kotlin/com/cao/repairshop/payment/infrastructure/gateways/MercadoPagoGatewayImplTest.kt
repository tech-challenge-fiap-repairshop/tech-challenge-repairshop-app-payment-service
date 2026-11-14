package com.cao.repairshop.payment.infrastructure.gateways

import com.cao.repairshop.payment.infrastructure.mercadopago.MercadoPagoClient
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.MercadoPagoPaymentResponse
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.MercadoPagoRefundResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class MercadoPagoGatewayImplTest {

    private lateinit var client: MercadoPagoClient
    private lateinit var gateway: MercadoPagoGatewayImpl

    @BeforeEach
    fun setup() {
        client = mockk()
        gateway = MercadoPagoGatewayImpl(client)
    }

    @Test
    fun `createPayment should map client response to PartnerPaymentResult`() {
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val response = MercadoPagoPaymentResponse(
            paymentId = "MP-PAY-1",
            status = "PENDING",
            qrCode = "qr-code-test",
            qrCodeBase64 = "base64-test",
            ticketUrl = "https://mp.com/ticket",
            isFallback = false
        )

        every { client.createPayment(any()) } returns response

        val result = gateway.createPayment(orderId, customerId, 199.9, "Serviço Mecânico")

        assertEquals("MP-PAY-1", result.paymentId)
        assertEquals("PENDING", result.status)
        assertEquals("qr-code-test", result.qrCode)
        assertFalse(result.isFallback)
    }

    @Test
    fun `refundPayment should map client refund response to PartnerRefundResult`() {
        val response = MercadoPagoRefundResponse(
            refundId = "REF-123",
            paymentId = "MP-PAY-1",
            status = "approved",
            amount = 199.9,
            isFallback = false
        )

        every { client.refundPayment("MP-PAY-1", 199.9, "Cancelamento") } returns response

        val result = gateway.refundPayment("MP-PAY-1", 199.9, "Cancelamento")

        assertEquals("REF-123", result.refundId)
        assertEquals("approved", result.status)
        assertEquals(199.9, result.amount)
        assertFalse(result.isFallback)
    }
}

