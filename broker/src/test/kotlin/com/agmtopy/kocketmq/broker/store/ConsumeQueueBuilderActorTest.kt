package com.agmtopy.kocketmq.broker.store

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ConsumeQueueBuilderActor测试
 */
class ConsumeQueueBuilderActorTest {

    private val testDir = "/tmp/kocketmq/test/consumequeue"
    private lateinit var builder: ConsumeQueueBuilderActor

    @BeforeEach
    fun setUp() {
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()
    }

    @AfterEach
    fun tearDown() = runBlocking {
        builder.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test build and query`() = runBlocking {
        builder = ConsumeQueueBuilderActor(testDir)
        builder.start()

        // 构建索引
        builder.buildConsumeQueue("TestTopic", 0, 100L, 50, 0)
        builder.buildConsumeQueue("TestTopic", 0, 200L, 60, 0)
        builder.buildConsumeQueue("TestTopic", 0, 300L, 70, 0)

        // 查询索引
        val offsets = builder.getPhysicOffsets("TestTopic", 0, 0, 10)

        assertEquals(3, offsets.size)
        assertEquals(100L, offsets[0])
        assertEquals(200L, offsets[1])
        assertEquals(300L, offsets[2])
    }

    @Test
    fun `test multiple queues`() = runBlocking {
        builder = ConsumeQueueBuilderActor(testDir)
        builder.start()

        // 为不同队列构建索引
        builder.buildConsumeQueue("TopicA", 0, 100L, 50)
        builder.buildConsumeQueue("TopicA", 1, 150L, 50)
        builder.buildConsumeQueue("TopicB", 0, 200L, 60)

        // 查询TopicA-Queue0
        val offsetsA0 = builder.getPhysicOffsets("TopicA", 0, 0, 10)
        assertEquals(1, offsetsA0.size)
        assertEquals(100L, offsetsA0[0])

        // 查询TopicA-Queue1
        val offsetsA1 = builder.getPhysicOffsets("TopicA", 1, 0, 10)
        assertEquals(1, offsetsA1.size)
        assertEquals(150L, offsetsA1[0])

        // 查询TopicB-Queue0
        val offsetsB0 = builder.getPhysicOffsets("TopicB", 0, 0, 10)
        assertEquals(1, offsetsB0.size)
        assertEquals(200L, offsetsB0[0])

        // 查询不存在的队列
        val offsetsNotExist = builder.getPhysicOffsets("TopicC", 0, 0, 10)
        assertEquals(0, offsetsNotExist.size)
    }

    @Test
    fun `test flush`() = runBlocking {
        builder = ConsumeQueueBuilderActor(testDir)
        builder.start()

        // 构建索引
        builder.buildConsumeQueue("TestTopic", 0, 100L, 50)

        // 刷盘
        builder.flush()

        // 应该能成功完成
        assertTrue(true)
    }

    @Test
    fun `test get queue count`() = runBlocking {
        builder = ConsumeQueueBuilderActor(testDir)
        builder.start()

        assertEquals(0, builder.getQueueCount())

        builder.buildConsumeQueue("Topic1", 0, 100L, 50)
        assertEquals(1, builder.getQueueCount())

        builder.buildConsumeQueue("Topic1", 1, 150L, 50)
        assertEquals(2, builder.getQueueCount())

        builder.buildConsumeQueue("Topic2", 0, 200L, 60)
        assertEquals(3, builder.getQueueCount())
    }
}
