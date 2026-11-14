package com.cao.repairshop.payment.application.usecases

import com.cao.repairshop.payment.infrastructure.controller.dtos.WebhookNotification

interface ProcessWebhookUseCase {
    fun execute(notification: WebhookNotification): Boolean
}

