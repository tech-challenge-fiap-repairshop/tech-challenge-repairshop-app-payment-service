package com.cao.repairshop.payment.core.health

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * HealthIndicator especializado para monitorar a saúde de listeners e consumidores SQS no payment-service.
 * Evita "Silent Zombie Pods" no Kubernetes (EKS).
 */
@Component("sqsListener")
class SqsListenerHealthIndicator(
    @Value("\${repairshop.sqs.health-check.enabled:true}")
    private val enabled: Boolean = true,
    @Value("\${repairshop.sqs.health-check.max-idle-seconds:120}")
    private val defaultMaxIdleSeconds: Long = 120
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

        @JvmStatic
        fun reset() {
            queueHeartbeats.clear()
            queueMaxIdleConfig.clear()
        }
    }

    override fun health(): Health {
        if (!enabled) {
            return Health.up()
                .withDetail("status", "DISABLED")
                .withDetail("message", "SQS Health check explicitamente desabilitado por configuração")
                .build()
        }

        if (queueHeartbeats.isEmpty()) {
            return Health.up()
                .withDetail("status", "STANDBY")
                .withDetail("message", "Nenhum listener SQS ativo ou registrado no momento")
                .build()
        }

        val now = Instant.now()
        val builder = Health.up()
        var hasFailures = false
        val failedQueues = mutableListOf<String>()

        queueHeartbeats.forEach { (queueName, lastHeartbeat) ->
            val maxIdle = queueMaxIdleConfig[queueName] ?: defaultMaxIdleSeconds
            val elapsedSeconds = Duration.between(lastHeartbeat, now).seconds

            builder.withDetail("queue.$queueName.last_heartbeat", lastHeartbeat.toString())
            builder.withDetail("queue.$queueName.elapsed_seconds", elapsedSeconds)
            builder.withDetail("queue.$queueName.max_idle_seconds", maxIdle)

            if (elapsedSeconds > maxIdle) {
                hasFailures = true
                failedQueues.add("$queueName (idle: ${elapsedSeconds}s > max: ${maxIdle}s)")
            }
        }

        return if (hasFailures) {
            logger.error("SQS Listener Health Check FALHOU: Queues travadas: {}", failedQueues)
            Health.down()
                .withDetail("failed_queues", failedQueues)
                .withDetail("reason", "Uma ou mais filas SQS ultrapassaram o tempo limite de heartbeat (possível Silent Zombie Pod)")
                .build()
        } else {
            builder
                .withDetail("monitored_queues_count", queueHeartbeats.size)
                .build()
        }
    }
}
