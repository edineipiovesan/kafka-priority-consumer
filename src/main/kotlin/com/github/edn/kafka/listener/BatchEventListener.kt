package com.github.edn.kafka.listener

import com.github.edn.event.kafka.MyAvroEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono
import java.time.OffsetDateTime
import kotlin.system.measureNanoTime

@Component
class BatchEventListener {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @KafkaListener(containerFactory = "batch-listener", topics = ["my.avro.topic"], concurrency = "4", groupId = "my.app.batch")
    fun onMessage(data: List<ConsumerRecord<String, MyAvroEvent>>, ack: Acknowledgment) {
        val elapsedNano = measureNanoTime {
            data.toFlux()
                    .flatMap {
                        logger.info("id=${it.value()?.id}")
                        it.toMono()
                    }
                    .collectList()
                    .doOnSubscribe {
                        logger.info("${OffsetDateTime.now()}: Starting batch processing...")
                    }
                    .doOnSuccess {
                        logger.info("${OffsetDateTime.now()}: Finished successfully batch processing!")
                        ack.acknowledge()
                    }
                    .doOnError {
                        logger.error("${OffsetDateTime.now()}: Failed batch processing!")
                    }
                    .subscribeOn(Schedulers.newBoundedElastic(64, 32, "BatchEventListener"))
                    .subscribe()
        }

        logger.info("batchSize=${data.size}; elapsedTime=${elapsedNano};")
    }
}