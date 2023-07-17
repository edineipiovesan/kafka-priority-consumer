package com.github.edn.kafka.offsetcontrol.datastore

import com.github.edn.kafka.consumer.RecordMetadata

interface OffsetDatastore {
    suspend fun registerOffset(metadata: RecordMetadata): RecordMetadata
    suspend fun retrieveLatestOffset(topic: String, partition: Set<Int>): List<Pair<Int, Long>>
}