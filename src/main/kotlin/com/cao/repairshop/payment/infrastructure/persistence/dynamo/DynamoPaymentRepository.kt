package com.cao.repairshop.payment.infrastructure.persistence.dynamo

import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.InvoiceDynamoItem
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.PaymentDynamoItem
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.time.Instant
import java.util.UUID

@Repository
class DynamoPaymentRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val enhancedClient: DynamoDbEnhancedClient,
    @Value("\${dynamodb.table.payment:tb-payment-dev}")
    private val tableName: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val paymentTable: DynamoDbTable<PaymentDynamoItem> by lazy {
        enhancedClient.table(tableName, TableSchema.fromBean(PaymentDynamoItem::class.java))
    }

    private val invoiceTable: DynamoDbTable<InvoiceDynamoItem> by lazy {
        enhancedClient.table(tableName, TableSchema.fromBean(InvoiceDynamoItem::class.java))
    }

    fun savePayment(payment: PaymentDynamoItem) {
        if (payment.pk.isBlank()) payment.pk = "BILLING#${payment.serviceOrderId}"
        if (payment.sk.isBlank()) {
            val ts = Instant.now().toEpochMilli()
            payment.sk = "PAY#$ts#${payment.paymentId}"
        }
        paymentTable.putItem(payment)
        logger.info("Item de pagamento salvo no DynamoDB: PK={}, SK={}, Valor={}", payment.pk, payment.sk, payment.priceValue)
    }

    fun saveInvoice(invoice: InvoiceDynamoItem) {
        if (invoice.pk.isBlank()) invoice.pk = "BILLING#${invoice.serviceOrderId}"
        if (invoice.sk.isBlank()) {
            val ts = Instant.now().toEpochMilli()
            invoice.sk = "INV#$ts#${invoice.invoiceId}"
        }
        invoiceTable.putItem(invoice)
        logger.info("Item de fatura salvo no DynamoDB: PK={}, SK={}, Número={}", invoice.pk, invoice.sk, invoice.invoiceNumber)
    }

    fun findByServiceOrderId(serviceOrderId: UUID): List<PaymentDynamoItem> {
        val pk = "BILLING#$serviceOrderId"
        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
            .expressionAttributeValues(
                mapOf(
                    ":pk" to AttributeValue.builder().s(pk).build(),
                    ":skPrefix" to AttributeValue.builder().s("PAY#").build()
                )
            )
            .build()

        return runCatching {
            val response = dynamoDbClient.query(request)
            response.items().map { item ->
                PaymentDynamoItem(
                    pk = item["PK"]?.s() ?: pk,
                    sk = item["SK"]?.s() ?: "",
                    entityType = item["entityType"]?.s() ?: "PAYMENT",
                    paymentId = item["payment_id"]?.s() ?: "",
                    serviceOrderId = item["service_order_id"]?.s() ?: serviceOrderId.toString(),
                    customerId = item["customer_id"]?.s() ?: "",
                    priceValue = item["price_value"]?.n()?.toDouble() ?: 0.0,
                    paymentDate = item["payment_date"]?.s() ?: "",
                    status = item["status"]?.s() ?: "PENDING",
                    paymentMethod = item["payment_method"]?.s() ?: "MERCADO_PAGO_QR",
                    osUpdateStatus = item["os_update_status"]?.s() ?: "PENDING",
                    paymentInfo = item["payment_info"]?.s() ?: "",
                    dispatchNotificateStatus = item["dispatch_notificate_status"]?.s() ?: "PENDING",
                    refundReason = item["refund_reason"]?.s(),
                    refundDate = item["refund_date"]?.s()
                )
            }
        }.getOrDefault(emptyList())
    }

    fun findById(id: String): PaymentDynamoItem? {
        val request = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("payment_id = :pid OR id = :pid")
            .expressionAttributeValues(mapOf(":pid" to AttributeValue.builder().s(id).build()))
            .build()

        val response = runCatching { dynamoDbClient.scan(request) }.getOrNull()
        val item = response?.items()?.firstOrNull() ?: return null
        return PaymentDynamoItem(
            pk = item["PK"]?.s() ?: "",
            sk = item["SK"]?.s() ?: "",
            entityType = item["entityType"]?.s() ?: "PAYMENT",
            paymentId = item["payment_id"]?.s() ?: id,
            serviceOrderId = item["service_order_id"]?.s() ?: "",
            customerId = item["customer_id"]?.s() ?: "",
            priceValue = item["price_value"]?.n()?.toDouble() ?: 0.0,
            paymentDate = item["payment_date"]?.s() ?: "",
            status = item["status"]?.s() ?: "PENDING",
            paymentMethod = item["payment_method"]?.s() ?: "MERCADO_PAGO_QR",
            osUpdateStatus = item["os_update_status"]?.s() ?: "PENDING",
            paymentInfo = item["payment_info"]?.s() ?: "",
            dispatchNotificateStatus = item["dispatch_notificate_status"]?.s() ?: "PENDING",
            refundReason = item["refund_reason"]?.s(),
            refundDate = item["refund_date"]?.s()
        )
    }

    fun updateStatus(pk: String, sk: String, newStatus: String, dispatchStatus: String? = null, osStatus: String? = null) {
        val updates = mutableListOf("#status = :newStatus")
        val values = mutableMapOf<String, AttributeValue>(
            ":newStatus" to AttributeValue.builder().s(newStatus).build()
        )
        val names = mutableMapOf("#status" to "status")

        if (dispatchStatus != null) {
            updates.add("dispatch_notificate_status = :ds")
            values[":ds"] = AttributeValue.builder().s(dispatchStatus).build()
        }
        if (osStatus != null) {
            updates.add("os_update_status = :os")
            values[":os"] = AttributeValue.builder().s(osStatus).build()
        }

        val request = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(mapOf(
                "PK" to AttributeValue.builder().s(pk).build(),
                "SK" to AttributeValue.builder().s(sk).build()
            ))
            .updateExpression("SET ${updates.joinToString(", ")}")
            .expressionAttributeNames(names)
            .expressionAttributeValues(values)
            .build()

        dynamoDbClient.updateItem(request)
        logger.info("Status do pagamento atualizado: PK={}, SK={}, status={}", pk, sk, newStatus)
    }

    fun refundPayment(pk: String, sk: String, reason: String) {
        val now = Instant.now().toString()
        val request = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(mapOf(
                "PK" to AttributeValue.builder().s(pk).build(),
                "SK" to AttributeValue.builder().s(sk).build()
            ))
            .updateExpression("SET #status = :refunded, refund_reason = :reason, refund_date = :rDate, os_update_status = :os, dispatch_notificate_status = :ds")
            .expressionAttributeNames(mapOf("#status" to "status"))
            .expressionAttributeValues(mapOf(
                ":refunded" to AttributeValue.builder().s("REFUNDED").build(),
                ":reason" to AttributeValue.builder().s(reason).build(),
                ":rDate" to AttributeValue.builder().s(now).build(),
                ":os" to AttributeValue.builder().s("UPDATED").build(),
                ":ds" to AttributeValue.builder().s("DISPATCHED").build()
            ))
            .build()

        dynamoDbClient.updateItem(request)
        logger.info("Estorno gravado no DynamoDB: PK={}, SK={}, Motivo={}", pk, sk, reason)
    }

    fun refundPaymentById(id: String, reason: String): PaymentDynamoItem? {
        val payment = findById(id) ?: return null
        refundPayment(payment.pk, payment.sk, reason)
        return payment.copy(
            status = "REFUNDED",
            refundReason = reason,
            refundDate = Instant.now().toString(),
            dispatchNotificateStatus = "DISPATCHED",
            osUpdateStatus = "UPDATED"
        )
    }
}

