package com.github.edn.kafka.parallel.offsetcontrol.datastore

import com.github.edn.kafka.parallel.RecordMetadata
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Component
class DynamoDbOffsetDatastore(private val dynamoDbClient: DynamoDbAsyncClient) : OffsetDatastore {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun registerOffset(metadata: RecordMetadata): RecordMetadata = suspendCoroutine { continuation ->
        logger.info("Registering offsets in DynamoDB table; metadata=$metadata")

        val items = mapOf<String, AttributeValue>(
            "topic" to AttributeValue.builder().s(metadata.topic).build(),
            "partition" to AttributeValue.builder().n(metadata.partition.toString()).build(),
            "offset" to AttributeValue.builder().n(metadata.offset.toString()).build(),
        )

        val request = PutItemRequest.builder()
            .tableName("offset-control")
            .item(items)
            .build()
            .also { logger.info("Request=$it") }

        try {
            dynamoDbClient.putItem(request).whenComplete { _, throwable ->
                val requestFailed = throwable != null

                if (requestFailed) {
                    continuation.resumeWithException(throwable)
                    logger.error("Failed to flush offsets to DynamoDB", throwable)
                } else {
                    continuation.resume(metadata)
                    logger.info("Offsets flushed to DynamoDB")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to call DynamoDB", e)
            continuation.resumeWithException(e)
        }
    }

    override suspend fun retrieveLatestOffset(topic: String, partition: List<Int>): List<Pair<Int, Long>> {
        TODO("Implement dyanmodb retrieve list of items")
    }
}

