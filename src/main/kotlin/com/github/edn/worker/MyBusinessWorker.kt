package com.github.edn.worker

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MyBusinessWorker {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    fun run() {
        logger.info("Running business logic")
    }
}