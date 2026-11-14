package com.cao.repairshop.payment

import com.cao.repairshop.payment.core.health.SqsListenerHealthIndicator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import java.lang.reflect.Field
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class SqsListenerHealthIndicatorTest {

    private fun clearHeartbeats() {
        val field: Field = SqsListenerHealthIndicator::class.java.getDeclaredField("queueHeartbeats")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(null) as ConcurrentHashMap<String, Instant>
        map.clear()
    }

    @Test
    fun `should return UP with DISABLED detail when health check is disabled`() {
        val indicator = SqsListenerHealthIndicator(enabled = false, defaultMaxIdleSeconds = 120)

        val health = indicator.health()

        assertEquals(Status.UP, health.status)
        assertEquals("DISABLED", health.details["status"])
    }

    @Test
    fun `should return UP with STANDBY detail when no queue is monitored`() {
        clearHeartbeats()
        val indicator = SqsListenerHealthIndicator(enabled = true, defaultMaxIdleSeconds = 120)

        val health = indicator.health()

        assertEquals(Status.UP, health.status)
        assertEquals("STANDBY", health.details["status"])
    }

    @Test
    fun `should return UP when monitored queue has fresh heartbeat`() {
        clearHeartbeats()
        val queue = "test-payment-queue"
        SqsListenerHealthIndicator.registerMonitoredQueue(queue, 60)
        SqsListenerHealthIndicator.recordHeartbeat(queue)

        val indicator = SqsListenerHealthIndicator(enabled = true, defaultMaxIdleSeconds = 120)
        val health = indicator.health()

        assertEquals(Status.UP, health.status)
        assertEquals(1, health.details["monitored_queues_count"])
    }

    @Test
    fun `should return DOWN when monitored queue exceeds max idle time`() {
        clearHeartbeats()
        val queue = "test-stalled-queue"
        // Register queue with max idle of 1 second
        SqsListenerHealthIndicator.registerMonitoredQueue(queue, 1)

        // Inject past timestamp
        val field: Field = SqsListenerHealthIndicator::class.java.getDeclaredField("queueHeartbeats")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(null) as ConcurrentHashMap<String, Instant>
        map[queue] = Instant.now().minusSeconds(10)

        val indicator = SqsListenerHealthIndicator(enabled = true, defaultMaxIdleSeconds = 1)
        val health = indicator.health()

        assertEquals(Status.DOWN, health.status)
        assertTrue(health.details.containsKey("failed_queues"))
        assertEquals("Uma ou mais filas SQS ultrapassaram o tempo limite de heartbeat (possível Silent Zombie Pod)", health.details["reason"])
    }
}
