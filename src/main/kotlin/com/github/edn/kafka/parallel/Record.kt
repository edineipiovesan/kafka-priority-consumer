package com.github.edn.kafka.parallel

import com.github.edn.event.kafka.MyAvroEvent
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

object Header {
    const val ID = "id"
    const val CREATE_TIME = "create-time"
    const val PROCESS_TIME_LIMIT = "process-time-limit"
}

sealed interface TTLAware<T : TTLAware<T>> {
    fun durationUntilExpires(): Duration
    fun isExpired(): Boolean = durationUntilExpires().toMillis() < 0
}

sealed interface IDAware<T : IDAware<T>> {
    fun id(): String
}

data class RecordMetadata(
    val topic: String,
    val partition: Int,
    val offset: Long
)

data class Record(
    val header: Map<String, String>,
    val data: MyAvroEvent,
    val metadata: RecordMetadata
) : IDAware<Record>, TTLAware<Record>, Comparable<Record> {
    override fun id(): String = data.id.toString()
    override fun durationUntilExpires(): Duration = Duration.between(OffsetDateTime.now(), expiresAt())
    fun createdAt(): OffsetDateTime = OffsetDateTime.parse(data.createdAt)
    fun expiresAt(): OffsetDateTime = createdAt().plus(data.timeToLive, ChronoUnit.MILLIS)
    override fun compareTo(other: Record): Int = durationUntilExpires().compareTo(other.durationUntilExpires())
}
