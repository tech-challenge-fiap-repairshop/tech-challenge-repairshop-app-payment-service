package br.com.fiap.repairshop.payment

import br.com.fiap.repairshop.payment.infrastructure.persistence.DynamoPaymentRepository
import br.com.fiap.repairshop.payment.infrastructure.persistence.PaymentDynamoItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.util.UUID

class DynamoPaymentRepositoryTest {

    private lateinit var dynamoDbClient: DynamoDbClient
    private lateinit var repository: DynamoPaymentRepository
    private val tableName = "tb-payment-test"

    @BeforeEach
    fun setUp() {
        dynamoDbClient = mock(DynamoDbClient::class.java)
        repository = DynamoPaymentRepository(dynamoDbClient, tableName)
    }

    @Test
    fun `should save payment with all attributes including originTraceId`() {
        val payment = PaymentDynamoItem(
            id = "pay-100",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 199.90,
            status = "PENDING",
            paymentMethod = "PIX",
            dispatchNotificateStatus = "PENDING",
            originTraceId = "00-trace-123-01"
        )

        repository.save(payment)

        val captor = ArgumentCaptor.forClass(PutItemRequest::class.java)
        verify(dynamoDbClient, times(1)).putItem(captor.capture())

        val request = captor.value
        assertEquals(tableName, request.tableName())
        assertEquals("PAYMENT#pay-100", request.item()["PK"]?.s())
        assertEquals("METADATA", request.item()["SK"]?.s())
        assertEquals("pay-100", request.item()["id"]?.s())
        assertEquals(payment.orderId.toString(), request.item()["order_id"]?.s())
        assertEquals(payment.customerId.toString(), request.item()["customer_id"]?.s())
        assertEquals("199.9", request.item()["amount"]?.n())
        assertEquals("PENDING", request.item()["status"]?.s())
        assertEquals("PIX", request.item()["payment_method"]?.s())
        assertEquals("00-trace-123-01", request.item()["origin_trace_id"]?.s())
    }

    @Test
    fun `should save payment without originTraceId`() {
        val payment = PaymentDynamoItem(
            id = "pay-101",
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            amount = 50.0,
            originTraceId = null
        )

        repository.save(payment)

        val captor = ArgumentCaptor.forClass(PutItemRequest::class.java)
        verify(dynamoDbClient).putItem(captor.capture())
        assertNull(captor.value.item()["origin_trace_id"])
    }

    @Test
    fun `should return payment when findById finds item`() {
        val id = "pay-found"
        val orderId = UUID.randomUUID()
        val customerId = UUID.randomUUID()

        val itemMap = mapOf(
            "id" to AttributeValue.builder().s(id).build(),
            "order_id" to AttributeValue.builder().s(orderId.toString()).build(),
            "customer_id" to AttributeValue.builder().s(customerId.toString()).build(),
            "amount" to AttributeValue.builder().n("120.5").build(),
            "status" to AttributeValue.builder().s("APPROVED").build(),
            "payment_method" to AttributeValue.builder().s("CREDIT_CARD").build(),
            "dispatch_notificate_status" to AttributeValue.builder().s("DISPATCHED").build(),
            "payment_date" to AttributeValue.builder().s("2026-10-26T00:00:00Z").build(),
            "origin_trace_id" to AttributeValue.builder().s("trace-abc").build()
        )

        val response = GetItemResponse.builder().item(itemMap).build()
        `when`(dynamoDbClient.getItem(any(GetItemRequest::class.java))).thenReturn(response)

        val result = repository.findById(id)

        assertNotNull(result)
        assertEquals(id, result?.id)
        assertEquals(orderId, result?.orderId)
        assertEquals(customerId, result?.customerId)
        assertEquals(120.5, result?.amount)
        assertEquals("APPROVED", result?.status)
        assertEquals("CREDIT_CARD", result?.paymentMethod)
        assertEquals("DISPATCHED", result?.dispatchNotificateStatus)
        assertEquals("trace-abc", result?.originTraceId)
    }

    @Test
    fun `should return null when findById returns empty response`() {
        val response = GetItemResponse.builder().item(emptyMap()).build()
        `when`(dynamoDbClient.getItem(any(GetItemRequest::class.java))).thenReturn(response)

        val result = repository.findById("unknown")

        assertNull(result)
    }

    @Test
    fun `should updateStatus with dispatchStatus provided`() {
        val id = "pay-update-1"
        repository.updateStatus(id, "APPROVED", "DISPATCHED")

        val captor = ArgumentCaptor.forClass(UpdateItemRequest::class.java)
        verify(dynamoDbClient, times(1)).updateItem(captor.capture())

        val request = captor.value
        assertEquals(tableName, request.tableName())
        assertEquals("PAYMENT#$id", request.key()["PK"]?.s())
        assertEquals("SET #status = :newStatus, #dns = :dns", request.updateExpression())
        assertEquals("APPROVED", request.expressionAttributeValues()[":newStatus"]?.s())
        assertEquals("DISPATCHED", request.expressionAttributeValues()[":dns"]?.s())
    }

    @Test
    fun `should updateStatus without dispatchStatus`() {
        val id = "pay-update-2"
        repository.updateStatus(id, "CANCELLED", null)

        val captor = ArgumentCaptor.forClass(UpdateItemRequest::class.java)
        verify(dynamoDbClient, times(1)).updateItem(captor.capture())

        val request = captor.value
        assertEquals("SET #status = :newStatus", request.updateExpression())
        assertEquals("CANCELLED", request.expressionAttributeValues()[":newStatus"]?.s())
        assertFalse(request.expressionAttributeValues().containsKey(":dns"))
    }
}
