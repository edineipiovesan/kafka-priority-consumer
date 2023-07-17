package com.github.edn.kafka.consumer

class ClosestToExpiresFirst<T>: Comparator<T> where T: TTLAware<T> {
    override fun compare(record1: T, record2: T): Int {
        return record2.durationUntilExpires().compareTo(record1.durationUntilExpires())
    }
}