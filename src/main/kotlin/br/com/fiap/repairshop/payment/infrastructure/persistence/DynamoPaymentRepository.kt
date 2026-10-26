package br.com.fiap.repairshop.payment.infrastructure.persistence

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.time.Instant
import java.util.UUID

data class PaymentDynamoItem(
    val id: String = UUID.randomUUID().toString(),
    val orderId: UUID,
    val customerId: UUID,
    val amount: Double,
    val status: String = "PENDING",
    val paymentMethod: String = "MERCADO_PAGO_QR",
    val dispatchNotificateStatus: String = "PENDING",
    val paymentDate: String = Instant.now().toString(),
    val originTraceId: String? = null
)

@Repository
class DynamoPaymentRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value("\${dynamodb.table.payment:tb-payment-dev}")
    private val tableName: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun save(payment: PaymentDynamoItem) {
        val item = mutableMapOf<String, AttributeValue>(
            "PK" to AttributeValue.builder().s("PAYMENT#${payment.id}").build(),
            "SK" to AttributeValue.builder().s("METADATA").build(),
            "id" to AttributeValue.builder().s(payment.id).build(),
            "order_id" to AttributeValue.builder().s(payment.orderId.toString()).build(),
            "customer_id" to AttributeValue.builder().s(payment.customerId.toString()).build(),
            "amount" to AttributeValue.builder().n(payment.amount.toString()).build(),
            "status" to AttributeValue.builder().s(payment.status).build(),
            "payment_method" to AttributeValue.builder().s(payment.paymentMethod).build(),
            "dispatch_notificate_status" to AttributeValue.builder().s(payment.dispatchNotificateStatus).build(),
            "payment_date" to AttributeValue.builder().s(payment.paymentDate).build()
        )

        payment.originTraceId?.let { item["origin_trace_id"] = AttributeValue.builder().s(it).build() }

        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build()

        dynamoDbClient.putItem(request)
        logger.info("Pagamento gravado no DynamoDB: PAYMENT#{}, Order={}, Status={}", payment.id, payment.orderId, payment.status)
    }

    fun findById(id: String): PaymentDynamoItem? {
        val key = mapOf(
            "PK" to AttributeValue.builder().s("PAYMENT#$id").build(),
            "SK" to AttributeValue.builder().s("METADATA").build()
        )

        val request = GetItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .build()

        val response = dynamoDbClient.getItem(request)
        if (!response.hasItem() || response.item().isEmpty()) return null

        val item = response.item()
        return PaymentDynamoItem(
            id = item["id"]!!.s(),
            orderId = UUID.fromString(item["order_id"]!!.s()),
            customerId = UUID.fromString(item["customer_id"]!!.s()),
            amount = item["amount"]?.n()?.toDouble() ?: 0.0,
            status = item["status"]!!.s(),
            paymentMethod = item["payment_method"]?.s() ?: "MERCADO_PAGO_QR",
            dispatchNotificateStatus = item["dispatch_notificate_status"]?.s() ?: "PENDING",
            paymentDate = item["payment_date"]?.s() ?: Instant.now().toString(),
            originTraceId = item["origin_trace_id"]?.s()
        )
    }

    fun updateStatus(id: String, newStatus: String, dispatchStatus: String? = null) {
        val key = mapOf(
            "PK" to AttributeValue.builder().s("PAYMENT#$id").build(),
            "SK" to AttributeValue.builder().s("METADATA").build()
        )

        val updateExpr = StringBuilder("SET #status = :newStatus")
        val names = mutableMapOf("#status" to "status")
        val values = mutableMapOf<String, AttributeValue>(
            ":newStatus" to AttributeValue.builder().s(newStatus).build()
        )

        if (dispatchStatus != null) {
            updateExpr.append(", #dns = :dns")
            names["#dns"] = "dispatch_notificate_status"
            values[":dns"] = AttributeValue.builder().s(dispatchStatus).build()
        }

        val request = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression(updateExpr.toString())
            .expressionAttributeNames(names)
            .expressionAttributeValues(values)
            .build()

        dynamoDbClient.updateItem(request)
        logger.info("Status do pagamento atualizado no DynamoDB: PAYMENT#{} -> {}", id, newStatus)
    }
}
