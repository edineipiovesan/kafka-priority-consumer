package com.github.edn.kafka.scheduler

import com.github.edn.event.kafka.MyAvroEvent
import com.github.edn.kafka.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Component
@EnableScheduling
class ProducerScheduler(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    companion object {
        const val TOPIC: String = "my.avro.topic"
    }

    @Scheduled(fixedDelay = 3, timeUnit = TimeUnit.SECONDS)
    fun produceRecord() {
        repeat(10) {
            UUID.randomUUID().toString()
                .run {
                    MyAvroEvent.newBuilder()
                        .setId(this)
                        .setMessage("[$this] This is just a message content.")
                        .setCreatedAt(OffsetDateTime.now().toString())
                        .setTimeToLive(if (Random.nextInt(0, 100) >= 90) 10000L else 500L)
                        .build()
                }
                .run { ProducerRecord<String, Any>(TOPIC, this.id.toString(), this) }
                .run { kafkaTemplate.send(this) }
        }
    }
}