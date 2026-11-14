package com.cao.repairshop.payment.infrastructure.persistence.dynamo.models

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import java.time.Instant

/**
 * Representa o item_invoice no DynamoDB tb-payment.
 * PK = BILLING#<service_order_id>
 * SK = INV#<timestamp>#<invoice_id>
 */
@DynamoDbBean
data class InvoiceDynamoItem(
    @get:DynamoDbPartitionKey
    @get:DynamoDbAttribute("PK")
    var pk: String = "",

    @get:DynamoDbSortKey
    @get:DynamoDbAttribute("SK")
    var sk: String = "",

    @get:DynamoDbAttribute("entityType")
    var entityType: String = "INVOICE",

    @get:DynamoDbAttribute("invoice_id")
    var invoiceId: String = "",

    @get:DynamoDbAttribute("service_order_id")
    var serviceOrderId: String = "",

    @get:DynamoDbAttribute("customer_id")
    var customerId: String = "",

    @get:DynamoDbAttribute("invoice_number")
    var invoiceNumber: String = "",

    @get:DynamoDbAttribute("price_value")
    var priceValue: Double = 0.0,

    @get:DynamoDbAttribute("emission_date")
    var emissionDate: String = Instant.now().toString(),

    @get:DynamoDbAttribute("invoice_info")
    var invoiceInfo: String = "",

    @get:DynamoDbAttribute("client_dispatch")
    var clientDispatch: String = "PENDING"
)

