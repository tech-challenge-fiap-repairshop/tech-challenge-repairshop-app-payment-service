package com.cao.repairshop.payment.application.usecases.impl

import com.cao.repairshop.payment.application.gateways.PaymentGateway
import com.cao.repairshop.payment.application.usecases.FindPaymentUseCase
import com.cao.repairshop.payment.domain.Payment
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FindPaymentUseCaseImpl(
    private val paymentGateway: PaymentGateway
) : FindPaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun findById(id: String): Payment? {
        logger.debug("Buscando pagamento por id: {}", id)
        return paymentGateway.findById(id)
    }

    override fun findByOrderId(orderId: UUID): List<Payment> {
        logger.debug("Buscando pagamentos por orderId: {}", orderId)
        return paymentGateway.findByOrderId(orderId)
    }
}

