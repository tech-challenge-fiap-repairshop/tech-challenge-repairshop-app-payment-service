package com.cao.repairshop.payment.infrastructure.controller.interfaces

import com.cao.repairshop.payment.infrastructure.controller.dtos.CreatePaymentRequest
import com.cao.repairshop.payment.infrastructure.controller.dtos.PaymentResponse
import com.cao.repairshop.payment.infrastructure.controller.dtos.RefundPaymentRequest
import com.cao.repairshop.payment.infrastructure.controller.dtos.WebhookNotification
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import java.util.UUID

@Tag(name = "Payments", description = "Endpoints de Pagamentos e Integração Mercado Pago")
interface PaymentApi {

    @Operation(summary = "Gera uma nova ordem de pagamento / cobrança via Mercado Pago para a OS")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Pagamento gerado com sucesso",
            content = [Content(schema = Schema(implementation = PaymentResponse::class))]),
        ApiResponse(responseCode = "400", description = "Erro de validação", content = [Content()])
    )
    fun createPaymentForOrder(serviceOrderId: UUID, request: CreatePaymentRequest): ResponseEntity<PaymentResponse>

    @Operation(summary = "Alias para criação de pagamento")
    fun createPayment(request: CreatePaymentRequest): ResponseEntity<PaymentResponse>

    @Operation(summary = "Consulta o status dos pagamentos de uma OS pelo serviceOrderId")
    fun getPaymentsByOrder(serviceOrderId: UUID): ResponseEntity<List<PaymentResponse>>

    @Operation(summary = "Consulta o status de um pagamento pelo ID")
    fun getPaymentById(id: String): ResponseEntity<PaymentResponse>

    @Operation(summary = "Confirmação de pagamento manual/balcão ou aprovação Pix")
    fun confirmPayment(id: String): ResponseEntity<PaymentResponse>

    @Operation(summary = "Realiza estorno/reembolso no Mercado Pago para compensação da Saga Pattern por ID")
    fun refundPayment(id: String, request: RefundPaymentRequest?): ResponseEntity<PaymentResponse>

    @Operation(summary = "Realiza estorno/reembolso de todos os pagamentos da OS para cancelamento compensatório")
    fun refundPaymentByOrder(serviceOrderId: UUID, request: RefundPaymentRequest?): ResponseEntity<List<PaymentResponse>>

    @Operation(summary = "Webhook de callback do Mercado Pago")
    fun webhook(notification: WebhookNotification): ResponseEntity<Map<String, String>>
}

