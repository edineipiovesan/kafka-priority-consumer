package com.github.edn.kafka.controller

import com.github.edn.kafka.producer.KafkaProducer
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/kafka")
class KafkaController(private val kafkaProducer: KafkaProducer) {

    @PostMapping("/produce")
    fun createWithDetails(@RequestBody request: Request): Response {
        return kafkaProducer.createEvent(
            topic = request.topic,
            eventCount = request.volume,
            internalMillis = request.interval
        )
    }
}