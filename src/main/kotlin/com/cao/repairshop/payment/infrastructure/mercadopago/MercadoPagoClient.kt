package com.cao.repairshop.payment.infrastructure.mercadopago

import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.MercadoPagoPaymentRequest
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.MercadoPagoPaymentResponse
import com.cao.repairshop.payment.infrastructure.mercadopago.dtos.MercadoPagoRefundResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

@Component
class MercadoPagoClient(
    @Value("\${mercadopago.access-token:#{null}}")
    private val accessToken: String?,
    @Value("\${mercadopago.base-url:https://api.mercadopago.com}")
    private val baseUrl: String = "https://api.mercadopago.com"
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    private fun shouldCallPartnerApi(): Boolean =
        baseUrl != "https://api.mercadopago.com" ||
            (!accessToken.isNullOrBlank() && !accessToken.startsWith("mock-offline") && !accessToken.startsWith("dev-dummy"))

    @CircuitBreaker(name = "mercadoPagoCircuitBreaker", fallbackMethod = "fallbackCreatePayment")
    fun createPayment(request: MercadoPagoPaymentRequest): MercadoPagoPaymentResponse {
        logger.info("Chamando gateway do Mercado Pago ({}) para OS {} no valor de R$ {}", baseUrl, request.orderId, request.amount)

        if (shouldCallPartnerApi()) {
            val payload = mapOf(
                "transaction_amount" to request.amount,
                "description" to request.description,
                "payment_method_id" to "pix",
                "payer" to mapOf("email" to "cliente@repairshop.com"),
                "external_reference" to request.orderId.toString()
            )
            val body = objectMapper.writeValueAsString(payload)

            val httpReqBuilder = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/v1/payments"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))

            if (!accessToken.isNullOrBlank()) {
                httpReqBuilder.header("Authorization", "Bearer $accessToken")
            }

            val httpResp = httpClient.send(httpReqBuilder.build(), HttpResponse.BodyHandlers.ofString())
            if (httpResp.statusCode() in 200..299) {
                val json = runCatching { objectMapper.readTree(httpResp.body()) }.getOrNull()
                val paymentId = json?.get("id")?.asText() ?: ("MP-" + UUID.randomUUID().toString().take(10).uppercase())
                val qrCode = json?.path("point_of_interaction")?.path("transaction_data")?.path("qr_code")?.asText()
                    ?: ("00020126580014BR.GOV.BCB.PIX0136${UUID.randomUUID()}520400005303986540${request.amount}5802BR5925OFICINA REPAIRSHOP6009SAO PAULO")
                val ticketUrl = json?.path("point_of_interaction")?.path("transaction_data")?.path("ticket_url")?.asText()
                    ?: "https://www.mercadopago.com.br/payments/$paymentId/ticket"

                logger.info("Pagamento gerado com sucesso via Mercado Pago: PaymentId={}", paymentId)
                return MercadoPagoPaymentResponse(
                    paymentId = paymentId,
                    status = "PENDING",
                    qrCode = qrCode,
                    ticketUrl = ticketUrl,
                    isFallback = false
                )
            } else {
                logger.warn("Erro ao chamar endpoint Mercado Pago: HTTP {} - {}", httpResp.statusCode(), httpResp.body())
                throw RuntimeException("Falha na chamada ao Mercado Pago: HTTP ${httpResp.statusCode()}")
            }
        }

        val paymentId = "MP-" + UUID.randomUUID().toString().take(12).uppercase()
        val qrCodePix = "00020126580014BR.GOV.BCB.PIX0136${UUID.randomUUID()}520400005303986540${request.amount}5802BR5925OFICINA REPAIRSHOP S/A6009SAO PAULO62070503***6304"

        return MercadoPagoPaymentResponse(
            paymentId = paymentId,
            status = "PENDING",
            qrCode = qrCodePix,
            ticketUrl = "https://www.mercadopago.com.br/payments/$paymentId/ticket",
            isFallback = false
        )
    }

    fun fallbackCreatePayment(request: MercadoPagoPaymentRequest, t: Throwable): MercadoPagoPaymentResponse {
        logger.warn("Circuit Breaker acionado para Mercado Pago na OS {}. Motivo: {}. Fallback ativado...",
            request.orderId, t.message)

        val fallbackPaymentId = "FALLBACK-MP-" + UUID.randomUUID().toString().take(8).uppercase()
        val contingenceQrCode = "00020126580014BR.GOV.BCB.PIX0136contingencia-oficina-repairshop520400005303986540${request.amount}5802BR5925OFICINA REPAIRSHOP6009SAO PAULO"

        return MercadoPagoPaymentResponse(
            paymentId = fallbackPaymentId,
            status = "PENDING",
            qrCode = contingenceQrCode,
            ticketUrl = "https://oficina-repairshop.local/contingency-receipt/$fallbackPaymentId",
            isFallback = true
        )
    }

    @CircuitBreaker(name = "mercadoPagoCircuitBreaker", fallbackMethod = "fallbackRefundPayment")
    fun refundPayment(paymentId: String, amount: Double, reason: String?): MercadoPagoRefundResponse {
        logger.info("Iniciando chamada de estorno no Mercado Pago para PaymentId {} no valor de R$ {}", paymentId, amount)

        if (shouldCallPartnerApi()) {
            val payload = mapOf(
                "amount" to amount,
                "metadata" to mapOf("reason" to (reason ?: "Cancelamento compensatorio"))
            )
            val body = objectMapper.writeValueAsString(payload)

            val httpReqBuilder = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/v1/payments/$paymentId/refunds"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))

            if (!accessToken.isNullOrBlank()) {
                httpReqBuilder.header("Authorization", "Bearer $accessToken")
            }

            val httpResp = httpClient.send(httpReqBuilder.build(), HttpResponse.BodyHandlers.ofString())
            if (httpResp.statusCode() in 200..299) {
                val json = runCatching { objectMapper.readTree(httpResp.body()) }.getOrNull()
                val refundId = json?.get("id")?.asText() ?: ("REF-MP-" + UUID.randomUUID().toString().take(10).uppercase())
                logger.info("Estorno aprovado pelo Mercado Pago: RefundId={}", refundId)
                return MercadoPagoRefundResponse(
                    refundId = refundId,
                    paymentId = paymentId,
                    status = "REFUNDED",
                    amount = amount,
                    isFallback = false
                )
            } else {
                logger.warn("Erro ao solicitar estorno no Mercado Pago: HTTP {} - {}", httpResp.statusCode(), httpResp.body())
                throw RuntimeException("Falha no estorno do Mercado Pago: HTTP ${httpResp.statusCode()}")
            }
        }

        val refundId = "REF-MP-" + UUID.randomUUID().toString().take(10).uppercase()
        return MercadoPagoRefundResponse(
            refundId = refundId,
            paymentId = paymentId,
            status = "REFUNDED",
            amount = amount,
            isFallback = false
        )
    }

    fun fallbackRefundPayment(paymentId: String, amount: Double, reason: String?, t: Throwable): MercadoPagoRefundResponse {
        logger.warn("Circuit Breaker acionado para estorno no Mercado Pago no PaymentId {}. Motivo: {}. Fallback ativado...",
            paymentId, t.message)

        val fallbackRefundId = "FALLBACK-REF-" + UUID.randomUUID().toString().take(8).uppercase()
        return MercadoPagoRefundResponse(
            refundId = fallbackRefundId,
            paymentId = paymentId,
            status = "REFUNDED",
            amount = amount,
            isFallback = true
        )
    }
}
