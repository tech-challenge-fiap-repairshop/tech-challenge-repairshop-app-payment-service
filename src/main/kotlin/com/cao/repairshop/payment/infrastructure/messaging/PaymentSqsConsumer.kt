package com.cao.repairshop.payment.infrastructure.messaging

import com.cao.repairshop.payment.application.usecases.CreatePaymentUseCase
import com.cao.repairshop.payment.core.health.SqsListenerHealthIndicator
import com.cao.repairshop.payment.infrastructure.controller.dtos.CreatePaymentRequest
import com.cao.repairshop.payment.infrastructure.messaging.dtos.OsReadyForPaymentInboundEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

@Component
class PaymentSqsConsumer(
    private val sqsClient: SqsClient,
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val objectMapper: ObjectMapper,
    @Value("\${sqs.queue.os-ready-for-payment:os-ready-for-payment-queue-dev}")
    private val osReadyForPaymentQueue: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        SqsListenerHealthIndicator.registerMonitoredQueue(osReadyForPaymentQueue, 120)
    }

    private fun getQueueUrl(queueName: String): String {
        return runCatching {
            sqsClient.getQueueUrl { it.queueName(queueName) }.queueUrl()
        }.getOrElse { queueName }
    }

    @Scheduled(fixedDelay = 5000)
    fun pollOsReadyForPayment() {
        val queueUrl = getQueueUrl(osReadyForPaymentQueue)
        runCatching {
            val response = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(2)
                    .messageAttributeNames("All")
                    .build()
            )

            SqsListenerHealthIndicator.recordHeartbeat(osReadyForPaymentQueue)

            for (msg in response.messages()) {
                val event = objectMapper.readValue(msg.body(), OsReadyForPaymentInboundEvent::class.java)
                logger.info("Consumindo evento OS pronta para cobrança: OS={}, Cliente={}, Valor={}", event.orderId, event.customerId, event.amount)

                val request = CreatePaymentRequest(
                    orderId = event.orderId,
                    customerId = event.customerId,
                    amount = event.amount
                )
                createPaymentUseCase.execute(event.orderId, request)

                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(msg.receiptHandle())
                        .build()
                )
                logger.info("Mensagem consumida e removida da fila SQS: OS={}", event.orderId)
            }
        }.onFailure { ex ->
            logger.error("Erro no polling da fila SQS {}: {}", osReadyForPaymentQueue, ex.message)
        }
    }
}

