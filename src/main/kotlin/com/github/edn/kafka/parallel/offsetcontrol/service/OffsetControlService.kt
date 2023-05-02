package com.github.edn.kafka.parallel.offsetcontrol.service

import com.github.edn.kafka.parallel.RecordMetadata
import com.github.edn.kafka.parallel.offsetcontrol.datastore.OffsetDatastore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class OffsetControlService(private val offsetDatastore: OffsetDatastore, syncInterval: Duration = 5.seconds) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val offsetSyncDispatcher = CoroutineScope(Executors.newFixedThreadPool(1).asCoroutineDispatcher())
    private val offsetControl = ConcurrentHashMap<String, ConcurrentHashMap<Int, List<Long>>>()

    init {
        tickerFlow(initialDelay = 0.seconds, period = syncInterval)
            .onEach {
                logger.info("Syncing offset...")
                syncOffsets()
            }.onCompletion {
                logger.error("onCompletion", it)
            }
            .launchIn(offsetSyncDispatcher)
    }

    fun registerProcessedOffsetPartitionAsync(metadata: RecordMetadata) {
        logger.info("Registering offset async; metadata=$metadata")
        val currentPartitions = offsetControl[metadata.topic] ?: ConcurrentHashMap<Int, List<Long>>()
        val currentOffsets = currentPartitions[metadata.partition] ?: listOf()

        val partitionOffset = ConcurrentHashMap<Int, List<Long>>().apply {
            offsetControl[metadata.topic]?.let { putAll(it) }
            put(metadata.partition, currentOffsets + metadata.offset)
        }

        offsetControl[metadata.topic] = partitionOffset

        logger.info("Current status $offsetControl")
        // TODO remove contiguous offsets at insertion?
    }

    suspend fun latestProcessedOffsetPartition(topic: String, partitions: List<Int>): List<Pair<Int, Long>> =
        offsetDatastore.retrieveLatestOffset(topic, partitions)

    private suspend fun syncOffsets() {
        offsetControl.forEach { (topic, partitionOffset) ->
            partitionOffset
                .mapValues { it.value.lastContiguous() }
                .filterValues { it != null }
                .map { (partition, offset) ->
                    println("Committing topic=$topic partition=$partition offset=$offset")
                    try {
                        val metadata = RecordMetadata(topic = topic, partition = partition, offset = offset!!)
                        offsetDatastore.registerOffset(metadata) // TODO: register in batch?
                        removeCommittedOffsets(partitionOffset, metadata)
                    } catch (e: Exception) {
                        logger.error("Failed to commmit offsets", e)
                    }
                }
        }
    }

    private fun removeCommittedOffsets(partitionOffset: ConcurrentHashMap<Int, List<Long>>, metadata: RecordMetadata) {
        partitionOffset[metadata.partition] = partitionOffset[metadata.partition]!!
            .filter { offset -> offset > metadata.offset }
    }

    private fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    @Suppress("ReturnCount")
    private fun List<Long>.lastContiguous(): Long? {
        if (isEmpty() || size == 1)
            return firstOrNull()

        val sorted = this.sorted()
        for ((index, currentNumber) in sorted.withIndex()) {
            val isLastIndex = index == sorted.lastIndex
            if (isLastIndex)
                return currentNumber

            val nextOffset = sorted[index + 1]
            val expectedOffset = currentNumber + 1
            val isContiguous = nextOffset == expectedOffset
            if (!isContiguous)
                return currentNumber
        }

        return null
    }
}