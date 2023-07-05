package com.github.edn.kafka.configuration

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
            val cluster = messageListenerContainer.containerProperties.kafkaConsumerProperties["bootstrap.servers"]
            val topics = messageListenerContainer.containerProperties.topics
            val assignedPartitions = messageListenerContainer.assignedPartitions?.groupBy { it.topic() }
                ?.mapValues { entry -> entry.value.map { topicPartition -> topicPartition.partition() } }
            val groupId = messageListenerContainer.groupId
            val listenerId = messageListenerContainer.listenerId

            // Running parameters
            val assignedPartitionsCount = messageListenerContainer.assignedPartitions?.size ?: 0
            val running = messageListenerContainer.isRunning &&
                    !messageListenerContainer.isContainerPaused &&
                    assignedPartitionsCount > 0

            // Metrics
            val maxLag = messageListenerContainer.metrics().values.firstOrNull()?.values?.firstOrNull {
                it.metricName().name() == "records-lag-max"
            }?.metricValue()

            val details = mutableMapOf(
                "topics" to topics,
                "assignedPartitions" to assignedPartitions,
                "groupId" to groupId,
                "listenerId" to listenerId,
                "isRunning" to messageListenerContainer.isRunning,
                "isContainerPaused" to messageListenerContainer.isContainerPaused,
                "recordsMaxLag" to maxLag
            )

            if (running) builder.up().withDetails(details)
            else builder.down().withDetails(details)
        }
    }
}