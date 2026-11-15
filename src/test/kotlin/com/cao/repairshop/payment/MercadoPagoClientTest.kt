package com.cao.repairshop.payment

import com.cao.repairshop.payment.infrastructure.mercadopago.MercadoPagoClient
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.MercadoPagoPaymentRequest
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.UUID

class MercadoPagoClientTest {

    private lateinit var client: MercadoPagoClient
    private var server: HttpServer? = null

    @BeforeEach
    fun setUp() {
        client = MercadoPagoClient(accessToken = "mock-offline-token")
    }

    @AfterEach
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    private fun startStubServer(path: String, status: Int, responseBody: String, onRequest: ((Map<String, List<String>>) -> Unit)? = null): String {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext(path) { exchange ->
            onRequest?.invoke(exchange.requestHeaders)
            val bytes = responseBody.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        srv.start()
        server = srv
        return "http://127.0.0.1:${srv.address.port}"
    }

    @Test
    fun `createPayment with sandbox token should return mock response`() {
        val request = MercadoPagoPaymentRequest(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
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
    fun `createPayment with dev-dummy or blank token on default url should return offline response`() {
        val request = MercadoPagoPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), 99.0)

        val r1 = MercadoPagoClient(accessToken = "dev-dummy-token").createPayment(request)
        val r2 = MercadoPagoClient(accessToken = null).createPayment(request)
        val r3 = MercadoPagoClient(accessToken = "   ").createPayment(request)

        assertTrue(r1.paymentId.startsWith("MP-"))
        assertTrue(r2.paymentId.startsWith("MP-"))
        assertTrue(r3.paymentId.startsWith("MP-"))
    }

    @Test
    fun `createPayment against partner API with bearer token should parse response fields`() {
        var authHeader: String? = null
        val baseUrl = startStubServer(
            path = "/v1/payments",
            status = 201,
            responseBody = """
                {
                  "id": "MP-REAL-9988",
                  "point_of_interaction": {
                    "transaction_data": {
                      "qr_code": "000201PIXREAL",
                      "ticket_url": "https://mp.test/ticket/9988"
                    }
                  }
                }
            """.trimIndent()
        ) { headers ->
            authHeader = headers["Authorization"]?.firstOrNull()
        }

        val httpClient = MercadoPagoClient(accessToken = "prod-secret-token", baseUrl = baseUrl)
        val response = httpClient.createPayment(
            MercadoPagoPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), 250.0, "Ordem 1")
        )

        assertEquals("Bearer prod-secret-token", authHeader)
        assertEquals("MP-REAL-9988", response.paymentId)
        assertEquals("PENDING", response.status)
        assertEquals("000201PIXREAL", response.qrCode)
        assertEquals("https://mp.test/ticket/9988", response.ticketUrl)
        assertFalse(response.isFallback)
    }

    @Test
    fun `createPayment against partner API with non-json body and null token should fallback to generated fields`() {
        var hasAuth = false
        val baseUrl = startStubServer(
            path = "/v1/payments",
            status = 200,
            responseBody = "not-json"
        ) { headers ->
            hasAuth = headers.containsKey("Authorization")
        }

        val httpClient = MercadoPagoClient(accessToken = null, baseUrl = baseUrl)
        val response = httpClient.createPayment(
            MercadoPagoPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), 80.0)
        )

        assertFalse(hasAuth)
        assertTrue(response.paymentId.startsWith("MP-"))
        assertTrue(response.qrCode!!.contains("80.0"))
        assertTrue(response.ticketUrl!!.contains(response.paymentId))
    }

    @Test
    fun `createPayment against partner API returning 500 should throw RuntimeException`() {
        val baseUrl = startStubServer(
            path = "/v1/payments",
            status = 500,
            responseBody = """{"error":"internal"}"""
        )
        val httpClient = MercadoPagoClient(accessToken = "token", baseUrl = baseUrl)

        val ex = assertThrows(RuntimeException::class.java) {
            httpClient.createPayment(MercadoPagoPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), 120.0))
        }
        assertTrue(ex.message!!.contains("HTTP 500"))
    }

    @Test
    fun `fallbackCreatePayment should return contingency approved mock response`() {
        val request = MercadoPagoPaymentRequest(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 150.0
        )
        val fallbackResponse = client.fallbackCreatePayment(request, RuntimeException("Mercado Pago API connection timeout"))

        assertNotNull(fallbackResponse)
        assertTrue(fallbackResponse.paymentId.startsWith("FALLBACK-MP-"))
        assertEquals("PENDING", fallbackResponse.status)
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
    fun `refundPayment against partner API with bearer token should parse refund id`() {
        var authHeader: String? = null
        val baseUrl = startStubServer(
            path = "/v1/payments/MP-555/refunds",
            status = 201,
            responseBody = """{"id":"REF-PARTNER-77"}"""
        ) { headers ->
            authHeader = headers["Authorization"]?.firstOrNull()
        }

        val httpClient = MercadoPagoClient(accessToken = "prod-token", baseUrl = baseUrl)
        val response = httpClient.refundPayment("MP-555", 300.0, "Desistencia")

        assertEquals("Bearer prod-token", authHeader)
        assertEquals("REF-PARTNER-77", response.refundId)
        assertEquals("MP-555", response.paymentId)
        assertEquals("REFUNDED", response.status)
        assertEquals(300.0, response.amount)
        assertFalse(response.isFallback)
    }

    @Test
    fun `refundPayment against partner API with null reason and invalid json should generate fallback refund id`() {
        var hasAuth = false
        val baseUrl = startStubServer(
            path = "/v1/payments/MP-556/refunds",
            status = 200,
            responseBody = "invalid-json"
        ) { headers ->
            hasAuth = headers.containsKey("Authorization")
        }

        val httpClient = MercadoPagoClient(accessToken = "", baseUrl = baseUrl)
        val response = httpClient.refundPayment("MP-556", 50.0, null)

        assertFalse(hasAuth)
        assertTrue(response.refundId.startsWith("REF-MP-"))
        assertEquals("REFUNDED", response.status)
    }

    @Test
    fun `refundPayment against partner API returning 400 should throw RuntimeException`() {
        val baseUrl = startStubServer(
            path = "/v1/payments/MP-557/refunds",
            status = 400,
            responseBody = """{"error":"bad request"}"""
        )
        val httpClient = MercadoPagoClient(accessToken = "prod-token", baseUrl = baseUrl)

        val ex = assertThrows(RuntimeException::class.java) {
            httpClient.refundPayment("MP-557", 50.0, "Erro")
        }
        assertTrue(ex.message!!.contains("HTTP 400"))
    }

    @Test
    fun `fallbackRefundPayment should return contingency refund response`() {
        val fallback = client.fallbackRefundPayment("MP-99999", 200.0, "Falha de rede", RuntimeException("API timeout"))

        assertNotNull(fallback)
        assertTrue(fallback.refundId.startsWith("FALLBACK-REF-"))
        assertEquals("MP-99999", fallback.paymentId)
        assertEquals("REFUNDED", fallback.status)
        assertEquals(200.0, fallback.amount)
        assertTrue(fallback.isFallback)
    }
}
