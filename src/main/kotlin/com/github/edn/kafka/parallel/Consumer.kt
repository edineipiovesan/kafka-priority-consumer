package com.github.edn.kafka.parallel

import com.github.edn.event.kafka.MyAvroEvent
import com.github.edn.kafka.parallel.offsetcontrol.datastore.DynamoDbOffsetDatastore
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.Headers
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchAcknowledgingConsumerAwareMessageListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class Consumer(private val dynamoDbOffsetDatastore: DynamoDbOffsetDatastore) :
    BatchAcknowledgingConsumerAwareMessageListener<String, MyAvroEvent>, ConsumerSeekAware {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val queue = RecordPriorityQueue<Record>(
        size = 1_000_000,
        parallelism = 64,
        priorityStrategy = ClosestToExpiresFirst()
    )
    private val processor = RecordProcessor(queue, dynamoDbOffsetDatastore)

    init {
        processor.start()
    }

    @KafkaListener(
        containerFactory = "batch-listener",
        topics = ["my.avro.topic"],
        concurrency = "3",
        groupId = "my.app.batch",
    )
    override fun onMessage(
        data: List<ConsumerRecord<String, MyAvroEvent>>,
        acknowledgment: Acknowledgment?,
        consumer: Consumer<*, *>,
    ) {
        logger.info("Batch received; size=${data.size}; partition=${data.firstOrNull()?.partition()}")

        val records = data.map {
            Record(
                header = it.headers().toStringMap(),
                data = it.value(), metadata = RecordMetadata(
                    topic = it.topic(),
                    partition = it.partition(),
                    offset = it.offset()
                )
            )
        }

        queue.enqueue(records)

        acknowledgment?.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: MutableMap<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback,
    ) {
        super.onPartitionsAssigned(assignments, callback)
        val topic = assignments.keys.first().topic()
        val partitions = assignments.keys.map { it.partition() }.toSet()

        runBlocking {
            dynamoDbOffsetDatastore.retrieveLatestOffset(topic, partitions)
                .forEach {
                    // check received offset < database offset
                    callback.seek(topic, it.first, it.second)
                }
        }
    }

    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
        super.onPartitionsRevoked(partitions)
        queue.clear()
    }
}

private fun Headers.toStringMap(): Map<String, String> =
    mapNotNull { it.key() to it.value().decodeToString() }.toMap()

// fun main() {
//
//
//     val dispatcher = CoroutineScope(Executors.newFixedThreadPool(4).asCoroutineDispatcher())
//     repeat(4) {
//         dispatcher.launch {
//             produce(queue = queue, size = 1_000_000, delay = 0..30L, timeToLive = 50)
//         }
//     }
// }
//
// fun produce(queue: RecordPriorityQueue<Record>, size: Int, delay: LongRange, timeToLive: Long) {
//     repeat((0..size).count()) {
//         Thread.sleep(Random.nextLong(delay.first, delay.last))
//         val headers = mapOf(
//             ID to it.toString(),
//             CREATE_TIME to OffsetDateTime.now().toString(),
//             PROCESS_TIME_LIMIT to timeToLive.toString()
//         )
//
//         val record = Record(headers)
//         queue.enqueue(record)
//     }
// }