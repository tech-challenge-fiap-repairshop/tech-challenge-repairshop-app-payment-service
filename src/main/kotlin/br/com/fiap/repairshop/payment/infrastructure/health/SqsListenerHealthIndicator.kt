package br.com.fiap.repairshop.payment.infrastructure.health

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * HealthIndicator especializado para monitorar listeners SQS do Payment Service (PF-11).
 * Detecta Silent Zombie Pods e aciona a livenessProbe do Kubernetes para auto-reinício.
 */
@Component("sqsListener")
class SqsListenerHealthIndicator(
    @Value("\${payment.sqs.health-check.enabled:true}")
    private val enabled: Boolean,
    @Value("\${payment.sqs.health-check.max-idle-seconds:120}")
    private val defaultMaxIdleSeconds: Long
) : HealthIndicator {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val queueHeartbeats = ConcurrentHashMap<String, Instant>()
        private val queueMaxIdleConfig = ConcurrentHashMap<String, Long>()

        @JvmStatic
        fun recordHeartbeat(queueName: String) {
            queueHeartbeats[queueName] = Instant.now()
        }

        @JvmStatic
        fun registerMonitoredQueue(queueName: String, maxIdleSeconds: Long? = null) {
            queueHeartbeats.putIfAbsent(queueName, Instant.now())
            if (maxIdleSeconds != null) {
                queueMaxIdleConfig[queueName] = maxIdleSeconds
            }
        }
    }

    override fun health(): Health {
        if (!enabled) {
            return Health.up().withDetail("status", "DISABLED").build()
        }

        if (queueHeartbeats.isEmpty()) {
            return Health.up()
                .withDetail("status", "STANDBY")
                .withDetail("message", "Nenhum listener SQS registrado no Payment Service")
                .build()
        }

        val now = Instant.now()
        val builder = Health.up()
        var hasFailures = false
        val failedQueues = mutableListOf<String>()

        queueHeartbeats.forEach { (queueName, lastHeartbeat) ->
            val maxIdle = queueMaxIdleConfig[queueName] ?: defaultMaxIdleSeconds
            val elapsedSeconds = Duration.between(lastHeartbeat, now).seconds

            builder.withDetail("queue.$queueName.elapsed_seconds", elapsedSeconds)
            if (elapsedSeconds > maxIdle) {
                hasFailures = true
                failedQueues.add("$queueName (${elapsedSeconds}s > ${maxIdle}s)")
            }
        }

        return if (hasFailures) {
            logger.error("Payment Service - SQS Listener Health FALHOU: Queues travadas: {}", failedQueues)
            Health.down()
                .withDetail("failed_queues", failedQueues)
                .withDetail("reason", "Heartbeat expirado em filas de pagamento (Possível Silent Zombie Pod)")
                .build()
        } else {
            builder.withDetail("monitored_queues_count", queueHeartbeats.size).build()
        }
    }
}
