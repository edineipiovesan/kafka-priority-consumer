package com.github.edn.kafka.consumer

import com.github.edn.kafka.offsetcontrol.datastore.DynamoDbOffsetDatastore
import com.github.edn.kafka.offsetcontrol.service.OffsetControlService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class RecordProcessor<T : Record>(
    val queue: RecordPriorityQueue<T>,
    private val dynamoDbOffsetDatastore: DynamoDbOffsetDatastore
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val isRunning = AtomicBoolean()
    private val dispatcher = Executors.newFixedThreadPool(queue.parallelism).asCoroutineDispatcher()
    private val offsetControlService = OffsetControlService(dynamoDbOffsetDatastore)

    init {
        repeat(queue.parallelism) {
            CoroutineScope(dispatcher).launch {
                while (isRunning.get()) {
                    val record = queue.channel.receive()
                    process(record)
                }
            }
        }
    }

    fun start() = isRunning.getAndSet(true)
        .also { logger.info("Starting process") }

    fun pause() = isRunning.getAndSet(false)
        .also { logger.info("Pause process") }

    suspend fun process(record: T) {
        try {
            val isExpired = record.isExpired()
            val durationUntilExpires = record.durationUntilExpires()
            val duration = measureTimeMillis {
                val processingTime = if (Random.nextInt(0, 100) >= 90)
                    Random.nextLong(200, 2000)
                else Random.nextLong(20, 50)
                if (!isExpired) {
                    delay(processingTime)
                }
            }
            logRecord(record, durationUntilExpires, isExpired, duration)
        } finally {
            offsetControlService.registerProcessedOffsetPartitionAsync(record.metadata)
        }
    }

    private fun logRecord(record: T, durationUntilExpires: Duration, isExpired: Boolean, duration: Long) {
        logger.info(
            buildString {
                // append("id=${record.id()}; ")
                append("duration=${duration}; ")
                append("durationUntilExpire: ${durationUntilExpires.toMillis()}; ")
                // append("createdAt=${record.createdAt()}; ")
                // append("expiresAt=${record.expiresAt()}; ")
                // append("timeToLive=${record.data.timeToLive}; ")
                append("isExpired=${isExpired}")
            }
        )
    }
}