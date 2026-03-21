package com.agmtopy.kocketmq.broker.perf

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.broker.store.MessageExt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能基准测试
 *
 * 测试指标：
 * 1. 消息发送吞吐量 (TPS)
 * 2. 消息拉取吞吐量 (TPS)
 * 3. 端到端延迟 (P50/P90/P99)
 * 4. 并发性能
 * 5. Actor 模型背压性能
 */
class PerformanceBenchmarkTest {

    private lateinit var brokerController: BrokerController
    private val testStorePath = File("target/perf_test_store").absolutePath

    @BeforeEach
    fun setup() = runBlocking {
        // 清理旧的测试数据
        File(testStorePath).deleteRecursively()

        val brokerConfig = BrokerConfig(
            brokerName = "PerformanceTestBroker",
            brokerId = 0,
            clusterName = "TestCluster",
            listenPort = 10919,
            storePathRootDir = testStorePath,
            storePathCommitLog = "$testStorePath/commitlog",
            commitLogFileSize = 1024 * 1024 * 100, // 100MB
            autoCreateTopicEnable = true,
            defaultTopicQueueNums = 8
        )

        brokerController = BrokerController(brokerConfig)
        brokerController.initialize()
        brokerController.start()
    }

    @AfterEach
    fun teardown() {
        runBlocking {
            brokerController.shutdown()
        }
        File(testStorePath).deleteRecursively()
    }

    /**
     * 测试消息发送吞吐量
     *
     * 目标：单线程发送 10万条消息，测量 TPS
     */
    @Test
    fun `test message send throughput`() = runBlocking {
        val messageCount = 100_000
        val topic = "PerfTestTopic"
        val startTime = System.currentTimeMillis()

        repeat(messageCount) { index ->
            val message = MessageExt(
                topic = topic,
                queueId = 0,
                body = "Performance test message $index".toByteArray(),
                bornTimestamp = System.currentTimeMillis()
            )

            brokerController.messageStore.putMessage(message)
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val tps = messageCount.toDouble() / duration * 1000

        println("\n========== 发送吞吐量测试 ==========")
        println("消息数量: $messageCount")
        println("总耗时: ${duration}ms")
        println("发送TPS: ${"%.2f".format(tps)} 条/秒")
        println("====================================\n")

        // 断言：TPS 应该大于 5000
        assertTrue(tps > 5000, "发送TPS应该大于5000，实际为${"%.2f".format(tps)}")
    }

    /**
     * 测试消息拉取吞吐量
     *
     * 目标：发送10万条消息后，拉取测量 TPS
     */
    @Test
    fun `test message pull throughput`() = runBlocking {
        val messageCount = 100_000
        val topic = "PerfTestTopic"

        // 1. 先发送消息
        println("\n准备发送 $messageCount 条消息...")
        repeat(messageCount) { index ->
            val message = MessageExt(
                topic = topic,
                queueId = 0,
                body = "Performance test message $index".toByteArray(),
                bornTimestamp = System.currentTimeMillis()
            )
            brokerController.messageStore.putMessage(message)
        }
        println("消息发送完成，开始拉取测试...")

        // 2. 拉取消息并测量吞吐量
        val startTime = System.currentTimeMillis()
        var pulledCount = 0
        var offset = 0L

        while (pulledCount < messageCount) {
            val result = brokerController.messageStore.getMessages(topic, 0, offset, 32)
            if (result.isNotEmpty()) {
                pulledCount += result.size
                offset += result.size
            } else {
                break
            }
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val tps = pulledCount.toDouble() / duration * 1000

        println("\n========== 拉取吞吐量测试 ==========")
        println("拉取消息数量: $pulledCount")
        println("总耗时: ${duration}ms")
        println("拉取TPS: ${"%.2f".format(tps)} 条/秒")
        println("====================================\n")

        // 断言：TPS 应该大于 3000
        assertTrue(tps > 3000, "拉取TPS应该大于3000，实际为${"%.2f".format(tps)}")
    }

    /**
     * 测试端到端延迟
     *
     * 目标：测量从发送到拉取的完整延迟分布 (P50/P90/P99)
     */
    @Test
    fun `test end-to-end latency`() = runBlocking {
        val messageCount = 10_000
        val topic = "LatencyTestTopic"
        val latencies = mutableListOf<Long>()

        repeat(messageCount) { index ->
            val sendTime = System.nanoTime()
            val message = MessageExt(
                topic = topic,
                queueId = 0,
                body = "Latency test message $index".toByteArray(),
                bornTimestamp = System.currentTimeMillis()
            )

            brokerController.messageStore.putMessage(message)

            // 立即拉取
            val result = brokerController.messageStore.getMessage(topic, 0, index.toLong())
            val receiveTime = System.nanoTime()

            latencies.add((receiveTime - sendTime) / 1_000_000) // 转换为毫秒
        }

        latencies.sort()
        val p50 = latencies[latencies.size * 50 / 100]
        val p90 = latencies[latencies.size * 90 / 100]
        val p99 = latencies[latencies.size * 99 / 100]
        val avg = latencies.average()

        println("\n========== 端到端延迟测试 ==========")
        println("消息数量: $messageCount")
        println("平均延迟: ${"%.2f".format(avg)}ms")
        println("P50延迟: ${p50}ms")
        println("P90延迟: ${p90}ms")
        println("P99延迟: ${p99}ms")
        println("====================================\n")

        // 断言：P99延迟应该小于 100ms
        assertTrue(p99 < 100, "P99延迟应该小于100ms，实际为${p99}ms")
    }

    /**
     * 测试并发发送性能
     *
     * 目标：多线程并发发送，测试 Actor 模型的并发性能
     */
    @Test
    fun `test concurrent send performance`() = runBlocking {
        val threadCount = 10
        val messagesPerThread = 10_000
        val totalMessages = threadCount * messagesPerThread
        val topic = "ConcurrentTestTopic"
        val successCount = AtomicLong(0)
        val latch = CountDownLatch(threadCount)

        val startTime = System.currentTimeMillis()

        // 启动多个协程并发发送
        val jobs = List(threadCount) { threadId ->
            launch(Dispatchers.Default) {
                repeat(messagesPerThread) { index ->
                    val message = MessageExt(
                        topic = topic,
                        queueId = threadId % 4, // 分散到不同队列
                        body = "Concurrent message thread-$threadId index-$index".toByteArray(),
                        bornTimestamp = System.currentTimeMillis()
                    )

                    try {
                        brokerController.messageStore.putMessage(message)
                        successCount.incrementAndGet()
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }
                latch.countDown()
            }
        }

        latch.await(5, TimeUnit.MINUTES)
        val endTime = System.currentTimeMillis()

        val duration = endTime - startTime
        val tps = successCount.get().toDouble() / duration * 1000

        println("\n========== 并发发送测试 ==========")
        println("线程数: $threadCount")
        println("每线程消息数: $messagesPerThread")
        println("总消息数: $totalMessages")
        println("成功发送: ${successCount.get()}")
        println("总耗时: ${duration}ms")
        println("并发TPS: ${"%.2f".format(tps)} 条/秒")
        println("====================================\n")

        // 断言：成功率应该大于 99%
        val successRate = successCount.get().toDouble() / totalMessages
        assertTrue(successRate > 0.99, "成功率应该大于99%，实际为${"%.2f".format(successRate * 100)}%")

        // 断言：并发TPS应该大于 10000
        assertTrue(tps > 10000, "并发TPS应该大于10000，实际为${"%.2f".format(tps)}")
    }

    /**
     * 测试 Actor 模型背压性能
     *
     * 目标：测试在高负载下的背压和流量控制
     */
    @Test
    fun `test backpressure performance`() = runBlocking {
        val messageCount = 50_000
        val topic = "BackpressureTestTopic"
        val channel = Channel<Int>(capacity = Channel.UNLIMITED)

        val startTime = System.currentTimeMillis()

        // 生产者：快速发送消息
        val producerJob = launch(Dispatchers.Default) {
            repeat(messageCount) { index ->
                val message = MessageExt(
                    topic = topic,
                    queueId = 0,
                    body = "Backpressure test message $index".toByteArray(),
                    bornTimestamp = System.currentTimeMillis()
                )

                brokerController.messageStore.putMessage(message)
                channel.send(index)
            }
            channel.close()
        }

        // 消费者：统计发送速率
        var sentCount = 0
        for (msg in channel) {
            sentCount++
        }

        producerJob.join()
        val endTime = System.currentTimeMillis()

        val duration = endTime - startTime
        val tps = sentCount.toDouble() / duration * 1000

        println("\n========== 背压性能测试 ==========")
        println("消息数量: $messageCount")
        println("实际发送: $sentCount")
        println("总耗时: ${duration}ms")
        println("TPS: ${"%.2f".format(tps)} 条/秒")
        println("====================================\n")

        // 断言：所有消息都应该发送成功
        assertEquals(messageCount, sentCount, "所有消息都应该发送成功")
    }
}
