package com.cao.repairshop.payment.infrastructure.controller.dtos

import java.util.UUID

data class WebhookNotification(
    val id: String,
    val status: String,
    val orderId: UUID? = null
)

