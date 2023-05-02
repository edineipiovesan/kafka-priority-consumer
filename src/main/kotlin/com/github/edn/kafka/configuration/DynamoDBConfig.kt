package com.github.edn.kafka.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.time.Duration

@Configuration
class DynamoDBConfig {
    @Bean
    fun client(): DynamoDbAsyncClient = DynamoDbAsyncClient.builder()
        .httpClientBuilder(
            NettyNioAsyncHttpClient.builder()
                .maxConcurrency(100)
                .connectionTimeout(Duration.ofSeconds(2))
        )
        .region(Region.US_EAST_1)
        .build()
}