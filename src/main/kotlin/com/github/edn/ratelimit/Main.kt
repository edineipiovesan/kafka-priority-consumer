// package com.github.edn.kafka.ratelimit
//
// import io.github.bucket4j.Bandwidth
// import io.github.bucket4j.BucketConfiguration
// import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
// import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
// import io.lettuce.core.RedisClient
// import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.asCoroutineDispatcher
// import kotlinx.coroutines.runBlocking
// import java.time.Duration
// import java.util.UUID
// import java.util.concurrent.Executors
//
// fun wtf() {
//     // val logger = LoggerFactory.getLogger("Main")
//     val redisClient = RedisClient.create("redis://eYVX7EwVmmxKPCDmwMtyKVge8oLd2t81@localhost:6379/0")
//     val proxyManager = LettuceBasedProxyManager.builderFor(redisClient).withExpirationStrategy(
//         ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10))
//     ).build()
//     val limit = Bandwidth.simple(300, Duration.ofSeconds(1))
//     val bucketConfiguration = BucketConfiguration.builder().addLimit(limit).build()
//     // val bucket = proxyManager.builder().build("mybucket".toByteArray(), bucketConfiguration)
//     val keys = (0 until 1_000_000).map { UUID.randomUUID().toString() }
//
//     val coroutineScope = CoroutineScope(Executors.newFixedThreadPool(20).asCoroutineDispatcher())
//     runBlocking {
//         while (true) {
//             // coroutineScope.launch {
//                 val startAt = System.currentTimeMillis()
//                 val key = keys.random()
//                 proxyManager.builder().build(key.toByteArray(), bucketConfiguration).asBlocking().consume(1)
//                 // logger.info("Processed; key=$key; duration=${System.currentTimeMillis() - startAt}ms")
//             // }
//         }
//     }
// }