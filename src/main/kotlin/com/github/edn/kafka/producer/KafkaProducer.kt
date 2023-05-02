package com.github.edn.kafka.producer

import com.github.edn.event.kafka.MyAvroEvent
import com.github.edn.kafka.controller.Response
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.UUID

@Component
class KafkaProducer(private val kafkaTemplate: KafkaTemplate<String, Any>) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun createEvent(topic: String, eventCount: Long, internalMillis: Long): Response {
        logger.info("Starting production for $eventCount events...")

        (0..eventCount).map { UUID.randomUUID().toString() }
            .parallelStream()
            .forEach { id ->
                if (internalMillis > 0) Thread.sleep(internalMillis)

                try {
                    val value = MyAvroEvent.newBuilder()
                        .setId(id)
                        .setMessage("[$id] This is just a message content.")
                        .setCreatedAt(OffsetDateTime.now().toString())
                        .setTimeToLive(500L)
                        .build()

                    ProducerRecord<String, Any>(topic, id, value)
                        .also { kafkaTemplate.send(it) }
                } catch (e: Exception) {
                    logger.error("[$id] Kafka message delivery error; exception=${e::class.java}; message=${e.message}")
                    e.printStackTrace()
                }
            }

        return Response(ids = listOf(), topic = topic)
    }
}
