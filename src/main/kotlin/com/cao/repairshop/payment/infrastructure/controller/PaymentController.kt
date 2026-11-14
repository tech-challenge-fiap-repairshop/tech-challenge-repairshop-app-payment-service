package com.cao.repairshop.payment.infrastructure.controller

import com.cao.repairshop.payment.application.usecases.*
import com.cao.repairshop.payment.domain.mapper.toResponse
import com.cao.repairshop.payment.infrastructure.controller.dtos.CreatePaymentRequest
import com.cao.repairshop.payment.infrastructure.controller.dtos.PaymentResponse
import com.cao.repairshop.payment.infrastructure.controller.dtos.RefundPaymentRequest
import com.cao.repairshop.payment.infrastructure.controller.dtos.WebhookNotification
import com.cao.repairshop.payment.infrastructure.controller.interfaces.PaymentApi
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class PaymentController(
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val confirmPaymentUseCase: ConfirmPaymentUseCase,
    private val refundPaymentUseCase: RefundPaymentUseCase,
    private val findPaymentUseCase: FindPaymentUseCase,
    private val processWebhookUseCase: ProcessWebhookUseCase
) : PaymentApi {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/payment/{serviceOrderId}")
    override fun createPaymentForOrder(
        @PathVariable serviceOrderId: UUID,
        @RequestBody request: CreatePaymentRequest
    ): ResponseEntity<PaymentResponse> {
        logger.info("REST POST /payment/{} recebido: valor={}", serviceOrderId, request.amount)
        val payment = createPaymentUseCase.execute(serviceOrderId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(payment.toResponse())
    }

    @PostMapping("/payments")
    override fun createPayment(@RequestBody request: CreatePaymentRequest): ResponseEntity<PaymentResponse> {
        val orderId = request.orderId ?: UUID.randomUUID()
        return createPaymentForOrder(orderId, request)
    }

    @GetMapping("/payment/{serviceOrderId}")
    override fun getPaymentsByOrder(@PathVariable serviceOrderId: UUID): ResponseEntity<List<PaymentResponse>> {
        val payments = findPaymentUseCase.findByOrderId(serviceOrderId)
        return ResponseEntity.ok(payments.map { it.toResponse() })
    }

    @GetMapping("/payments/{id}")
    override fun getPaymentById(@PathVariable id: String): ResponseEntity<PaymentResponse> {
        val payment = findPaymentUseCase.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(payment.toResponse())
    }

    @PostMapping("/payment/{id}/confirm", "/payments/{id}/confirm")
    override fun confirmPayment(@PathVariable id: String): ResponseEntity<PaymentResponse> {
        logger.info("REST POST /payment/{}/confirm recebido", id)
        val payment = confirmPaymentUseCase.execute(id)
        return ResponseEntity.ok(payment.toResponse())
    }

    @PostMapping("/payment/{id}/refund", "/payments/{id}/refund")
    override fun refundPayment(
        @PathVariable id: String,
        @RequestBody(required = false) request: RefundPaymentRequest?
    ): ResponseEntity<PaymentResponse> {
        logger.info("REST POST /payment/{}/refund recebido", id)
        val payment = refundPaymentUseCase.executeById(id, request?.reason)
        return ResponseEntity.ok(payment.toResponse())
    }

    @PostMapping("/payment/order/{serviceOrderId}/refund")
    override fun refundPaymentByOrder(
        @PathVariable serviceOrderId: UUID,
        @RequestBody(required = false) request: RefundPaymentRequest?
    ): ResponseEntity<List<PaymentResponse>> {
        logger.info("REST POST /payment/order/{}/refund recebido", serviceOrderId)
        val refundedPayments = refundPaymentUseCase.executeByOrderId(serviceOrderId, request?.reason)
        return ResponseEntity.ok(refundedPayments.map { it.toResponse() })
    }

    @PostMapping("/payment/webhook", "/payments/webhook")
    override fun webhook(@RequestBody notification: WebhookNotification): ResponseEntity<Map<String, String>> {
        logger.info("REST POST /payment/webhook recebido: id={}, status={}", notification.id, notification.status)
        val processed = processWebhookUseCase.execute(notification)
        return ResponseEntity.ok(mapOf("status" to if (processed) "processed" else "not_found"))
    }
}

