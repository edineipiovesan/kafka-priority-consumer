package com.github.edn.kafka.parallel.offsetcontrol.datastore

import com.github.edn.kafka.parallel.RecordMetadata
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Component
class DynamoDbOffsetDatastore(private val dynamoDbClient: DynamoDbAsyncClient) : OffsetDatastore {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val TABLE_NAME = "offset-control"
    }

    override suspend fun registerOffset(metadata: RecordMetadata): RecordMetadata = suspendCoroutine { continuation ->
        logger.info("Registering offsets in DynamoDB table; metadata=$metadata")

        val items = mapOf<String, AttributeValue>(
            "topic" to AttributeValue.builder().s(metadata.topic).build(),
            "partition" to AttributeValue.builder().n(metadata.partition.toString()).build(),
            "offset" to AttributeValue.builder().n(metadata.offset.toString()).build(),
        )

        val request = PutItemRequest.builder()
            .tableName(TABLE_NAME)
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

    override suspend fun retrieveLatestOffset(topic: String, partition: Set<Int>): List<Pair<Int, Long>> {

        return suspendCoroutine { continuation ->
            val keys = partition.map {
                mapOf(
                    "topic" to AttributeValue.builder().s(topic).build(),
                    "partition" to AttributeValue.builder().n(it.toString()).build()
                )
            }

            val requestItems = mapOf<String, KeysAndAttributes>(
                TABLE_NAME to KeysAndAttributes.builder()
                    .consistentRead(false)
                    .keys(keys)
                    .build()
            )

            val request = BatchGetItemRequest.builder()
                .requestItems(requestItems)
                .build()

            dynamoDbClient.batchGetItem(request)
                .whenComplete { response, throwable ->
                    val requestFailed = throwable != null

                    if (requestFailed) {
                        continuation.resumeWithException(throwable)
                        logger.error("Failed to retrieve offsets from DynamoDB", throwable)
                    } else {
                        val result = response.responses()[TABLE_NAME]
                            ?.map { it["partition"]!!.n().toInt() to it["offset"]!!.n().toLong() }
                        if (result != null) continuation.resume(result)
                        else continuation.resumeWithException(IllegalStateException("topic not found on datastore"))
                    }
                }
        }
    }
}

