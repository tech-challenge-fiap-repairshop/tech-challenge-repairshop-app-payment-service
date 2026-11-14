package com.cao.repairshop.payment.core.metrics

import com.cao.repairshop.payment.domain.PaymentStatus
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BusinessMetricsBinder : MeterBinder {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun bindTo(registry: MeterRegistry) {
        logger.info("Initializing Payment & Billing Business Micrometer Metrics...")

        Gauge.builder("repairshop_business_revenue_total_brl") { 0.0 }
            .description("Receita total faturada em BRL")
            .register(registry)

        Gauge.builder("repairshop_business_average_ticket_brl") { 0.0 }
            .description("Ticket médio por ordem de serviço faturada em BRL")
            .register(registry)

        Gauge.builder("repairshop_business_invoices") { 0.0 }
            .description("Total de notas fiscais/faturas emitidas")
            .register(registry)

        PaymentStatus.entries.forEach { status ->
            Gauge.builder("repairshop_business_payments_by_status") { 0.0 }
                .tag("status", status.name)
                .description("Total de pagamentos por status: ${status.name}")
                .register(registry)
        }
    }
}

