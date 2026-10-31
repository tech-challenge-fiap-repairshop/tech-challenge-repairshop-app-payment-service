package br.com.fiap.repairshop.payment.infrastructure.persistence

import br.com.fiap.repairshop.payment.infrastructure.persistence.models.InvoiceDynamoItem
import br.com.fiap.repairshop.payment.infrastructure.persistence.models.PaymentDynamoItem
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
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

    /**
     * Salva o item_payment no DynamoDB tb-payment
     */
    fun savePayment(payment: PaymentDynamoItem) {
        if (payment.pk.isBlank()) payment.pk = "BILLING#${payment.serviceOrderId}"
        if (payment.sk.isBlank()) {
            val ts = Instant.now().toEpochMilli()
            payment.sk = "PAY#$ts#${payment.paymentId}"
        }
        paymentTable.putItem(payment)
        logger.info("Pagamento gravado no DynamoDB tb-payment: PK={}, SK={}, Valor={}", payment.pk, payment.sk, payment.priceValue)
    }

    /**
     * Salva o item_invoice no DynamoDB tb-payment
     */
    fun saveInvoice(invoice: InvoiceDynamoItem) {
        if (invoice.pk.isBlank()) invoice.pk = "BILLING#${invoice.serviceOrderId}"
        if (invoice.sk.isBlank()) {
            val ts = Instant.now().toEpochMilli()
            invoice.sk = "INV#$ts#${invoice.invoiceId}"
        }
        invoiceTable.putItem(invoice)
        logger.info("Fatura gravada no DynamoDB tb-payment: PK={}, SK={}, Número={}", invoice.pk, invoice.sk, invoice.invoiceNumber)
    }

    /**
     * Busca todos os itens de pagamento para uma dada OS
     */
    fun findByServiceOrderId(serviceOrderId: UUID): List<PaymentDynamoItem> {
        val pk = "BILLING#$serviceOrderId"
        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
            .expressionAttributeValues(mapOf(
                ":pk" to AttributeValue.builder().s(pk).build(),
                ":skPrefix" to AttributeValue.builder().s("PAY#").build()
            ))
            .build()

        return runCatching {
            val response = dynamoDbClient.query(request)
            response.items().map { item ->
                mapToPaymentDynamoItem(item)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Busca um pagamento pelo payment_id (varrendo a partição ou scan leve)
     */
    fun findById(id: String): PaymentDynamoItem? {
        val request = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("payment_id = :pid OR id = :pid")
            .expressionAttributeValues(mapOf(":pid" to AttributeValue.builder().s(id).build()))
            .limit(1)
            .build()

        val response = runCatching { dynamoDbClient.scan(request) }.getOrNull()
        val item = response?.items()?.firstOrNull() ?: return null
        return mapToPaymentDynamoItem(item)
    }

    /**
     * Consulta pagamentos pendentes de notificação via GSI dispatch-notification-index
     */
    fun findPendingDispatchNotifications(): List<PaymentDynamoItem> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .indexName("dispatch-notification-index")
            .keyConditionExpression("dispatch_notificate_status = :dns")
            .expressionAttributeValues(mapOf(
                ":dns" to AttributeValue.builder().s("PENDING").build()
            ))
            .limit(25)
            .build()

        return runCatching {
            val response = dynamoDbClient.query(request)
            response.items().map { mapToPaymentDynamoItem(it) }
        }.getOrDefault(emptyList())
    }

    /**
     * Busca pagamentos aprovados que ainda estão pendentes de atualização na OS (os_update_status = 'PENDING')
     */
    fun findPendingOsUpdates(): List<PaymentDynamoItem> {
        val request = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("#status = :approved AND os_update_status = :pending")
            .expressionAttributeNames(mapOf("#status" to "status"))
            .expressionAttributeValues(mapOf(
                ":approved" to AttributeValue.builder().s("APPROVED").build(),
                ":pending" to AttributeValue.builder().s("PENDING").build()
            ))
            .limit(25)
            .build()

        return runCatching {
            val response = dynamoDbClient.scan(request)
            response.items().map { mapToPaymentDynamoItem(it) }
        }.getOrDefault(emptyList())
    }

    /**
     * Atualiza o status do pagamento, os_update_status e dispatch_notificate_status
     */
    fun updateStatus(
        pk: String,
        sk: String,
        newStatus: String,
        dispatchStatus: String? = null,
        osUpdateStatus: String? = null
    ) {
        val key = mapOf(
            "PK" to AttributeValue.builder().s(pk).build(),
            "SK" to AttributeValue.builder().s(sk).build()
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

        if (osUpdateStatus != null) {
            updateExpr.append(", #ous = :ous")
            names["#ous"] = "os_update_status"
            values[":ous"] = AttributeValue.builder().s(osUpdateStatus).build()
        }

        val request = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression(updateExpr.toString())
            .expressionAttributeNames(names)
            .expressionAttributeValues(values)
            .build()

        dynamoDbClient.updateItem(request)
        logger.info("Status do pagamento atualizado no DynamoDB: PK={}, SK={} -> {}", pk, sk, newStatus)
    }

    // Sobrecarga de conveniência para atualizar por paymentId
    fun updateStatus(id: String, newStatus: String, dispatchStatus: String? = null) {
        val payment = findById(id) ?: return
        updateStatus(payment.pk, payment.sk, newStatus, dispatchStatus, if (newStatus == "APPROVED") "PENDING" else null)
    }

    fun save(payment: PaymentDynamoItem) {
        savePayment(payment)
    }

    private fun mapToPaymentDynamoItem(item: Map<String, AttributeValue>): PaymentDynamoItem {
        val pid = item["payment_id"]?.s() ?: item["id"]?.s() ?: UUID.randomUUID().toString()
        val soId = item["service_order_id"]?.s() ?: item["order_id"]?.s() ?: ""
        val custId = item["customer_id"]?.s() ?: ""
        val pk = item["PK"]?.s() ?: "BILLING#$soId"
        val sk = item["SK"]?.s() ?: "PAY#${Instant.now().toEpochMilli()}#$pid"

        return PaymentDynamoItem(
            pk = pk,
            sk = sk,
            entityType = item["entityType"]?.s() ?: "PAYMENT",
            paymentId = pid,
            serviceOrderId = soId,
            customerId = custId,
            priceValue = item["price_value"]?.n()?.toDouble() ?: item["amount"]?.n()?.toDouble() ?: 0.0,
            paymentDate = item["payment_date"]?.s() ?: Instant.now().toString(),
            status = item["status"]?.s() ?: "PENDING",
            paymentMethod = item["payment_method"]?.s() ?: "MERCADO_PAGO_QR",
            osUpdateStatus = item["os_update_status"]?.s() ?: "PENDING",
            paymentInfo = item["payment_info"]?.s() ?: "",
            serviceOrderDispatchNotificate = item["service_order_dispatch_notificate"]?.s() ?: "PENDING",
            clientDispatchNotificate = item["client_dispatch_notificate"]?.s() ?: "PENDING",
            dispatchNotificateStatus = item["dispatch_notificate_status"]?.s() ?: "PENDING",
            originTraceId = item["origin_trace_id"]?.s()
        )
    }
}
