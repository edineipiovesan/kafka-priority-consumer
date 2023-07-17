package com.github.edn.configuration

import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.stereotype.Component

@Component("kafka_consumers")
class HealthConfiguration(
    private val kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry,
) : AbstractHealthIndicator() {
    override fun doHealthCheck(builder: Health.Builder) {
        val allListenerContainers = kafkaListenerEndpointRegistry
            .allListenerContainers

        allListenerContainers.forEach { messageListenerContainer ->
            // General info
            val assignedPartitions = messageListenerContainer.assignedPartitions?.groupBy { it.topic() }
                ?.mapValues { entry -> entry.value.map { topicPartition -> topicPartition.partition() } }

            // Running parameters
            val assignedPartitionsCount = messageListenerContainer.assignedPartitions?.size ?: 0
            val running = messageListenerContainer.isRunning &&
                    !messageListenerContainer.isContainerPaused &&
                    assignedPartitionsCount > 0

            // Metrics
            val recordsMaxLag = messageListenerContainer.metrics().values.firstOrNull()?.values?.firstOrNull {
                it.metricName().name() == "records-lag-max"
            }?.metricValue()

            // Health details
            val details = mutableMapOf(
                "topics" to messageListenerContainer.containerProperties.topics,
                "assignedPartitions" to assignedPartitions,
                "groupId" to messageListenerContainer.groupId,
                "listenerId" to messageListenerContainer.listenerId,
                "isRunning" to messageListenerContainer.isRunning,
                "isContainerPaused" to messageListenerContainer.isContainerPaused,
                "recordsMaxLag" to recordsMaxLag
            )

            if (running) builder.up().withDetails(details)
            else builder.down().withDetails(details)
        }
    }
}