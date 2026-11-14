package com.cao.repairshop.payment.core.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class BusinessMetricsBinderTest {

    @Test
    fun `should bind all payment business metrics successfully`() {
        val registry = SimpleMeterRegistry()
        val binder = BusinessMetricsBinder()

        binder.bindTo(registry)

        assertNotNull(registry.find("repairshop_business_revenue_total_brl").gauge())
        assertNotNull(registry.find("repairshop_business_average_ticket_brl").gauge())
        assertNotNull(registry.find("repairshop_business_invoices").gauge())
        assertNotNull(registry.find("repairshop_business_payments_by_status").tag("status", "APPROVED").gauge())
    }
}

