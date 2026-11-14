package com.cao.repairshop.payment

import com.cao.repairshop.payment.infrastructure.persistence.dynamo.DynamoPaymentRepository
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.InvoiceDynamoItem
import com.cao.repairshop.payment.infrastructure.persistence.dynamo.models.PaymentDynamoItem
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.util.UUID

class DynamoPaymentRepositoryTest {

    private val dynamoDbClient: DynamoDbClient = mockk(relaxed = true)
    private val enhancedClient: DynamoDbEnhancedClient = mockk(relaxed = true)
    private val paymentTable: DynamoDbTable<PaymentDynamoItem> = mockk(relaxed = true)
    private val invoiceTable: DynamoDbTable<InvoiceDynamoItem> = mockk(relaxed = true)
    private lateinit var repository: DynamoPaymentRepository
    private val tableName = "tb-payment-test"

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        every { enhancedClient.table(eq(tableName), any<TableSchema<*>>()) } answers {
            val schema = secondArg<TableSchema<*>>()
            if (schema.itemType().rawClass() == PaymentDynamoItem::class.java) {
                paymentTable
            } else {
                invoiceTable
            }
        }

        repository = DynamoPaymentRepository(dynamoDbClient, enhancedClient, tableName)
    }

    @Test
    fun `should save payment using enhanced client`() {
        val payment = PaymentDynamoItem(
            paymentId = "pay-100",
            serviceOrderId = UUID.randomUUID().toString(),
            customerId = UUID.randomUUID().toString(),
            priceValue = 199.90,
            status = "PENDING",
            paymentMethod = "PIX",
            dispatchNotificateStatus = "PENDING",
            originTraceId = "00-trace-123-01"
        )

        repository.savePayment(payment)

        verify(exactly = 1) { paymentTable.putItem(any<PaymentDynamoItem>()) }
        assertEquals("BILLING#${payment.serviceOrderId}", payment.pk)
        assertTrue(payment.sk.startsWith("PAY#"))
    }

    @Test
    fun `should save invoice using enhanced client`() {
        val invoice = InvoiceDynamoItem(
            invoiceId = "inv-500",
            serviceOrderId = UUID.randomUUID().toString(),
            customerId = UUID.randomUUID().toString(),
            invoiceNumber = "NF-00123",
            priceValue = 199.90
        )

        repository.saveInvoice(invoice)

        verify(exactly = 1) { invoiceTable.putItem(invoice) }
        assertEquals("BILLING#${invoice.serviceOrderId}", invoice.pk)
        assertTrue(invoice.sk.startsWith("INV#"))
    }

    @Test
    fun `should return payment when findById finds item via scan`() {
        val id = "pay-found"
        val orderId = UUID.randomUUID().toString()
        val customerId = UUID.randomUUID().toString()

        val itemMap = mapOf(
            "payment_id" to AttributeValue.builder().s(id).build(),
            "service_order_id" to AttributeValue.builder().s(orderId).build(),
            "customer_id" to AttributeValue.builder().s(customerId).build(),
            "price_value" to AttributeValue.builder().n("120.5").build(),
            "status" to AttributeValue.builder().s("APPROVED").build(),
            "payment_method" to AttributeValue.builder().s("CREDIT_CARD").build(),
            "dispatch_notificate_status" to AttributeValue.builder().s("DISPATCHED").build(),
            "payment_date" to AttributeValue.builder().s("2026-10-26T00:00:00Z").build(),
            "origin_trace_id" to AttributeValue.builder().s("trace-abc").build()
        )

        val response = ScanResponse.builder().items(listOf(itemMap)).build()
        every { dynamoDbClient.scan(any<ScanRequest>()) } returns response

        val result = repository.findById(id)

        assertNotNull(result)
        assertEquals(id, result?.paymentId)
        assertEquals(orderId, result?.serviceOrderId)
        assertEquals(customerId, result?.customerId)
        assertEquals(120.5, result?.priceValue)
        assertEquals("APPROVED", result?.status)
    }

    @Test
    fun `should return null when findById returns empty scan`() {
        val response = ScanResponse.builder().items(emptyList()).build()
        every { dynamoDbClient.scan(any<ScanRequest>()) } returns response

        val result = repository.findById("unknown")

        assertNull(result)
    }

    @Test
    fun `should updateStatus with dispatchStatus provided`() {
        val pk = "BILLING#order-1"
        val sk = "PAY#123#pay-1"

        val slot = slot<UpdateItemRequest>()
        every { dynamoDbClient.updateItem(capture(slot)) } returns mockk()

        repository.updateStatus(pk, sk, "APPROVED", "DISPATCHED", "PENDING")

        verify(exactly = 1) { dynamoDbClient.updateItem(any<UpdateItemRequest>()) }

        val request = slot.captured
        assertEquals(tableName, request.tableName())
        assertEquals(pk, request.key()["PK"]?.s())
        assertEquals(sk, request.key()["SK"]?.s())
        assertTrue(request.updateExpression().contains("#status = :newStatus"))
        assertTrue(request.updateExpression().contains("dispatch_notificate_status = :ds"))
        assertTrue(request.updateExpression().contains("os_update_status = :os"))
        assertEquals("APPROVED", request.expressionAttributeValues()[":newStatus"]?.s())
        assertEquals("DISPATCHED", request.expressionAttributeValues()[":ds"]?.s())
    }

    @Test
    fun `findByServiceOrderId should query items by order id PK`() {
        val orderId = UUID.randomUUID()
        val itemMap = mapOf(
            "PK" to AttributeValue.builder().s("BILLING#$orderId").build(),
            "SK" to AttributeValue.builder().s("PAY#1#pay-1").build(),
            "payment_id" to AttributeValue.builder().s("pay-1").build(),
            "service_order_id" to AttributeValue.builder().s(orderId.toString()).build(),
            "price_value" to AttributeValue.builder().n("99.9").build(),
            "status" to AttributeValue.builder().s("PENDING").build()
        )

        val response = QueryResponse.builder().items(listOf(itemMap)).build()
        every { dynamoDbClient.query(any<QueryRequest>()) } returns response

        val results = repository.findByServiceOrderId(orderId)

        assertEquals(1, results.size)
        assertEquals("pay-1", results.first().paymentId)
    }

    @Test
    fun `refundPaymentById should find item, update and return refunded item`() {
        val id = "pay-to-refund"
        val orderId = UUID.randomUUID().toString()
        val itemMap = mapOf(
            "PK" to AttributeValue.builder().s("BILLING#$orderId").build(),
            "SK" to AttributeValue.builder().s("PAY#1#$id").build(),
            "payment_id" to AttributeValue.builder().s(id).build(),
            "service_order_id" to AttributeValue.builder().s(orderId).build(),
            "price_value" to AttributeValue.builder().n("200.0").build(),
            "status" to AttributeValue.builder().s("APPROVED").build()
        )

        every { dynamoDbClient.scan(any<ScanRequest>()) } returns ScanResponse.builder().items(listOf(itemMap)).build()
        every { dynamoDbClient.updateItem(any<UpdateItemRequest>()) } returns mockk()

        val result = repository.refundPaymentById(id, "Cancelamento do cliente")

        assertNotNull(result)
        assertEquals("REFUNDED", result?.status)
        assertEquals("Cancelamento do cliente", result?.refundReason)
        verify(exactly = 1) { dynamoDbClient.updateItem(any<UpdateItemRequest>()) }
    }
}
