package br.com.fiap.repairshop.payment.infrastructure.web

import br.com.fiap.repairshop.payment.infrastructure.messaging.PaymentSqsMessenger
import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.PaymentDynamoItem
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreatePaymentRequest(
    val orderId: UUID,
    val customerId: UUID,
    val amount: Double,
    val paymentMethod: String? = "MERCADO_PAGO_QR"
)

data class WebhookNotification(
    val id: String,
    val status: String,
    val orderId: UUID? = null
)

@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Endpoints de Pagamentos e Integração Mercado Pago")
class PaymentController(
    private val paymentRepository: DynamoPaymentRepository,
    private val sqsMessenger: PaymentSqsMessenger
) {

    @PostMapping
    @Operation(summary = "Gera uma nova ordem de pagamento / cobrança")
    fun createPayment(@RequestBody request: CreatePaymentRequest): ResponseEntity<PaymentDynamoItem> {
        val payment = PaymentDynamoItem(
            orderId = request.orderId,
            customerId = request.customerId,
            amount = request.amount,
            paymentMethod = request.paymentMethod ?: "MERCADO_PAGO_QR"
        )
        paymentRepository.save(payment)
        return ResponseEntity.status(HttpStatus.CREATED).body(payment)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta o status de um pagamento pelo ID")
    fun getPayment(@PathVariable id: String): ResponseEntity<PaymentDynamoItem> {
        val payment = paymentRepository.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(payment)
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirmação de pagamento manual/balcão ou aprovação Pix")
    fun confirmPayment(@PathVariable id: String): ResponseEntity<PaymentDynamoItem> {
        val payment = paymentRepository.findById(id) ?: return ResponseEntity.notFound().build()
        paymentRepository.updateStatus(id, "APPROVED", "DISPATCHED")

        val updated = payment.copy(status = "APPROVED", dispatchNotificateStatus = "DISPATCHED")
        sqsMessenger.notifyPaymentSuccess(updated)

        return ResponseEntity.ok(updated)
    }

    @PostMapping("/webhook")
    @Operation(summary = "Webhook de callback do Mercado Pago")
    fun webhook(@RequestBody notification: WebhookNotification): ResponseEntity<Map<String, String>> {
        val payment = paymentRepository.findById(notification.id)
        if (payment != null) {
            val status = if (notification.status.equals("approved", true)) "APPROVED" else "REJECTED"
            paymentRepository.updateStatus(payment.id, status, "DISPATCHED")
            if (status == "APPROVED") {
                sqsMessenger.notifyPaymentSuccess(payment.copy(status = "APPROVED"))
            }
        }
        return ResponseEntity.ok(mapOf("status" to "processed"))
    }
}
