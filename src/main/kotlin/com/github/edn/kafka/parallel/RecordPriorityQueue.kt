package com.github.edn.kafka.parallel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RecordPriorityQueue<T>(
    val size: Int = Int.MAX_VALUE,
    val parallelism: Int = 4,
    priorityStrategy: Comparator<T>
) where T : Comparable<T> {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val priorityQueue: PriorityBlockingQueue<T> = PriorityBlockingQueue(size, priorityStrategy)
    private val pollerDispatcher = CoroutineScope(Executors.newFixedThreadPool(1).asCoroutineDispatcher())
    private val metricsDispatcher = CoroutineScope(Executors.newFixedThreadPool(1).asCoroutineDispatcher())

    val channel: Channel<T> = Channel(capacity = parallelism, onBufferOverflow = BufferOverflow.SUSPEND)

    init {
        pollerDispatcher.launch { while (true) channel.send(dequeue()) }

        tickerFlow(500.milliseconds)
            .onEach { logger.info("Size: ${priorityQueue.size};") }
            .launchIn(metricsDispatcher)
    }

    fun isFull(): Boolean = priorityQueue.size > size

    fun isNotFull(): Boolean = !isFull()

    fun willBeFullWith(amount: Long) = (priorityQueue.size + amount) > size

    fun willNotBeFullWith(amount: Long) = !willBeFullWith(amount)

    private fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    fun clear() = priorityQueue.clear()

    fun enqueue(data: T): Boolean {
        require(isNotFull()) {
            fullQueueExceptionMessage()
        }

        return priorityQueue.add(data)
    }

    fun enqueue(data: Collection<T>): Boolean {
        require(isNotFull() && willNotBeFullWith(data.size.toLong())) {
            fullQueueExceptionMessage()
        }

        return priorityQueue.addAll(data)
    }

    fun dequeue(): T = priorityQueue.take()

    private fun fullQueueExceptionMessage() =
        "Queue buffer reached. Item won't be added to avoid OutOfMemory. Size: $size; Current: ${priorityQueue.size}"
}
