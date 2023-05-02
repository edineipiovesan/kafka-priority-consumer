package com.github.edn.kafka.parallel

class ClosestToExpiresFirst<T>: Comparator<T> where T: TTLAware<T> {
    override fun compare(record1: T, record2: T): Int {
        return record2.durationUntilExpires().compareTo(record1.durationUntilExpires())
    }
}