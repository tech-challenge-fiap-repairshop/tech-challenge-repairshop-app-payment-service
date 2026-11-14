package com.cao.repairshop.payment.infrastructure.persistence.dynamo.models

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import java.time.Instant

/**
 * Representa o item_payment no DynamoDB tb-payment.
 * Mapeamento fiel do desenho de arquitetura da Fase 4:
 * PK = BILLING#<service_order_id>
 * SK = PAY#<timestamp>#<payment_id>
 * GSI dispatch-notification-index: PK = dispatch_notificate_status, SK = payment_date
 */
@DynamoDbBean
data class PaymentDynamoItem(
    @get:DynamoDbPartitionKey
    @get:DynamoDbAttribute("PK")
    var pk: String = "",

    @get:DynamoDbSortKey
    @get:DynamoDbAttribute("SK")
    var sk: String = "",

    @get:DynamoDbAttribute("entityType")
    var entityType: String = "PAYMENT",

    @get:DynamoDbAttribute("payment_id")
    var paymentId: String = "",

    @get:DynamoDbAttribute("service_order_id")
    var serviceOrderId: String = "",

    @get:DynamoDbAttribute("customer_id")
    var customerId: String = "",

    @get:DynamoDbAttribute("price_value")
    var priceValue: Double = 0.0,

    @get:DynamoDbSecondarySortKey(indexNames = ["dispatch-notification-index"])
    @get:DynamoDbAttribute("payment_date")
    var paymentDate: String = Instant.now().toString(),

    @get:DynamoDbAttribute("status")
    var status: String = "PENDING",

    @get:DynamoDbAttribute("payment_method")
    var paymentMethod: String = "MERCADO_PAGO_QR",

    @get:DynamoDbAttribute("os_update_status")
    var osUpdateStatus: String = "PENDING",

    @get:DynamoDbAttribute("payment_info")
    var paymentInfo: String = "",

    @get:DynamoDbAttribute("service_order_dispatch_notificate")
    var serviceOrderDispatchNotificate: String = "PENDING",

    @get:DynamoDbAttribute("client_dispatch_notificate")
    var clientDispatchNotificate: String = "PENDING",

    @get:DynamoDbSecondaryPartitionKey(indexNames = ["dispatch-notification-index"])
    @get:DynamoDbAttribute("dispatch_notificate_status")
    var dispatchNotificateStatus: String = "PENDING",

    @get:DynamoDbAttribute("origin_trace_id")
    var originTraceId: String? = null,

    @get:DynamoDbAttribute("refund_reason")
    var refundReason: String? = null,

    @get:DynamoDbAttribute("refund_date")
    var refundDate: String? = null
)

