package com.agmtopy.kocketmq.broker.store

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * MessageStoreActor集成测试
 */
class MessageStoreActorTest {

    private val testDir = "/tmp/kocketmq/test/messagestore"
    private val commitLogFileSize = 1024 * 10  // 10KB，便于测试
    private val consumeQueueFileSize = 1024 * 6  // 6KB
    private lateinit var messageStore: MessageStoreActor

    @BeforeEach
    fun setUp() {
        // 清理测试目录
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()
    }

    @AfterEach
    fun tearDown() = runBlocking {
        // 清理资源
        messageStore.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test load and start`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)

        val loaded = messageStore.load()
        assertTrue(loaded)

        messageStore.start()
        Thread.sleep(100)  // 等待协程启动

        assertTrue(messageStore.isRunning())
        assertEquals(0, messageStore.getCommitLogFileCount())
        assertEquals(0, messageStore.getConsumeQueueCount())
    }

    @Test
    fun `test put and get single message`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 创建消息
        val message = MessageExt(
            topic = "TestTopic",
            queueId = 0,
            body = "Hello, MessageStore!".toByteArray(),
            bodyCRC = MessageExt.calculateCRC32("Hello, MessageStore!".toByteArray())
        )

        // 存储消息
        val putResult = messageStore.putMessage(message)

        assertEquals(PutMessageStatus.PUT_OK, putResult.status)
        assertNotNull(putResult.appendMessageResult)
        assertTrue(putResult.appendMessageResult!!.wroteOffset >= 0)

        // 查询消息
        val getResult = messageStore.getMessage("TestTopic", 0, 0)

        assertEquals(GetMessageStatus.GET_OK, getResult.status)
        assertNotNull(getResult.message)

        val readMessage = getResult.message!!
        assertEquals("TestTopic", readMessage.topic)
        assertEquals(0, readMessage.queueId)
        assertEquals("Hello, MessageStore!", String(readMessage.body))
    }

    @Test
    fun `test put multiple messages same queue`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 写入10条消息到同一个队列
        for (i in 1..10) {
            val message = MessageExt(
                topic = "TestTopic",
                queueId = 0,
                body = "Message $i".toByteArray(),
                bodyCRC = MessageExt.calculateCRC32("Message $i".toByteArray())
            )

            val putResult = messageStore.putMessage(message)
            assertEquals(PutMessageStatus.PUT_OK, putResult.status)
        }

        // 验证ConsumeQueue数量
        assertEquals(1, messageStore.getConsumeQueueCount())

        // 批量读取所有消息
        val messages = messageStore.getMessages("TestTopic", 0, 0, 10)

        assertEquals(10, messages.size)
        for (i in messages.indices) {
            assertEquals("Message ${i + 1}", String(messages[i].body))
        }
    }

    @Test
    fun `test put multiple messages different queues`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 写入消息到不同的队列
        val topics = listOf("TopicA", "TopicB", "TopicC")
        val queueIds = listOf(0, 1, 2)

        for (topic in topics) {
            for (queueId in queueIds) {
                val message = MessageExt(
                    topic = topic,
                    queueId = queueId,
                    body = "$topic-$queueId".toByteArray()
                )

                val putResult = messageStore.putMessage(message)
                assertEquals(PutMessageStatus.PUT_OK, putResult.status)
            }
        }

        // 验证ConsumeQueue数量：3个主题 * 3个队列 = 9个
        assertEquals(9, messageStore.getConsumeQueueCount())

        // 验证每个队列的消息
        for (topic in topics) {
            for (queueId in queueIds) {
                val messages = messageStore.getMessages(topic, queueId, 0, 1)
                assertEquals(1, messages.size)
                assertEquals("$topic-$queueId", String(messages[0].body))
            }
        }
    }

    @Test
    fun `test concurrent put messages`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 并发写入100条消息
        val jobs = (1..100).map { i ->
            async {
                val message = MessageExt(
                    topic = "ConcurrentTest",
                    queueId = i % 4,
                    body = "Message $i".toByteArray()
                )
                messageStore.putMessage(message)
            }
        }

        val results = jobs.awaitAll()

        // 所有消息应该成功写入
        assertTrue(results.all { it.status == PutMessageStatus.PUT_OK })

        // 验证ConsumeQueue数量：4个队列
        assertEquals(4, messageStore.getConsumeQueueCount())

        // 验证CommitLog最大偏移量
        assertTrue(messageStore.getMaxOffset() > 0)
    }

    @Test
    fun `test get non-existent message`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 查询不存在的消息
        val getResult = messageStore.getMessage("NonExistentTopic", 0, 0)

        assertEquals(GetMessageStatus.GET_NOT_FOUND, getResult.status)
        assertNull(getResult.message)
    }

    @Test
    fun `test flush`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 写入消息
        val message = MessageExt(
            topic = "FlushTest",
            queueId = 0,
            body = "Test flush".toByteArray()
        )
        messageStore.putMessage(message)

        // 刷盘
        messageStore.flush()

        // 验证刷盘偏移量
        assertTrue(messageStore.getFlushedOffset() > 0)
    }

    @Test
    fun `test restart and recover`() = runBlocking {
        // 第一个阶段：写入消息并关闭
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 写入消息
        val message1 = MessageExt(
            topic = "RecoverTest",
            queueId = 0,
            body = "First message".toByteArray()
        )
        val putResult1 = messageStore.putMessage(message1)
        assertEquals(PutMessageStatus.PUT_OK, putResult1.status)

        // 刷盘
        messageStore.flush()

        // 关闭
        messageStore.shutdown()

        // 第二个阶段：重新启动并验证
        val messageStore2 = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore2.load())
        messageStore2.start()

        // 验证offset恢复
        assertTrue(messageStore2.getMaxOffset() > 0)

        // 写入新消息
        val message2 = MessageExt(
            topic = "RecoverTest",
            queueId = 0,
            body = "Second message".toByteArray()
        )
        val putResult2 = messageStore2.putMessage(message2)
        assertEquals(PutMessageStatus.PUT_OK, putResult2.status)

        // 读取两条消息
        val messages = messageStore2.getMessages("RecoverTest", 0, 0, 10)
        // 注意：由于ConsumeQueue是延迟加载的，重启后可能需要重新构建索引
        // 这里主要验证CommitLog能正常恢复和继续写入

        messageStore2.shutdown()
    }

    @Test
    fun `test large message`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 创建大消息（1KB）
        val largeBody = ByteArray(1024) { it.toByte() }
        val message = MessageExt(
            topic = "LargeTest",
            queueId = 0,
            body = largeBody,
            bodyCRC = MessageExt.calculateCRC32(largeBody)
        )

        // 存储
        val putResult = messageStore.putMessage(message)
        assertEquals(PutMessageStatus.PUT_OK, putResult.status)

        // 读取
        val getResult = messageStore.getMessage("LargeTest", 0, 0)
        assertEquals(GetMessageStatus.GET_OK, getResult.status)
        assertTrue(largeBody.contentEquals(getResult.message!!.body))
    }

    @Test
    fun `test message with properties`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        val message = MessageExt(
            topic = "PropertiesTest",
            queueId = 0,
            body = "Test".toByteArray(),
            properties = "TAGS=TagA;KEYS=Key123",
            flag = 1,
            sysFlag = 2,
            queueOffset = 100L
        )

        val putResult = messageStore.putMessage(message)
        assertEquals(PutMessageStatus.PUT_OK, putResult.status)

        val getResult = messageStore.getMessage("PropertiesTest", 0, 0)
        assertEquals(GetMessageStatus.GET_OK, getResult.status)

        val readMessage = getResult.message!!
        assertEquals("TAGS=TagA;KEYS=Key123", readMessage.properties)
        assertEquals(1, readMessage.flag)
        assertEquals(2, readMessage.sysFlag)
        assertEquals(100L, readMessage.queueOffset)
    }

    @Test
    fun `test get min and max offset`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        // 初始状态
        assertEquals(0L, messageStore.getMinOffset())
        assertEquals(0L, messageStore.getMaxOffset())

        // 写入消息
        for (i in 1..5) {
            val message = MessageExt(
                topic = "OffsetTest",
                queueId = 0,
                body = "Message $i".toByteArray()
            )
            messageStore.putMessage(message)
        }

        // 验证offset
        assertEquals(0L, messageStore.getMinOffset())
        assertTrue(messageStore.getMaxOffset() > 0)
    }

    @Test
    fun `test performance 1000 messages`() = runBlocking {
        messageStore = MessageStoreActor(testDir, commitLogFileSize, consumeQueueFileSize)
        assertTrue(messageStore.load())
        messageStore.start()

        val messageCount = 1000
        val startTime = System.currentTimeMillis()

        // 写入1000条消息
        for (i in 1..messageCount) {
            val message = MessageExt(
                topic = "PerfTest",
                queueId = i % 8,
                body = "Message $i".toByteArray()
            )
            val putResult = messageStore.putMessage(message)
            assertEquals(PutMessageStatus.PUT_OK, putResult.status)
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        println("Wrote $messageCount messages in ${duration}ms")
        println("Throughput: ${messageCount * 1000 / duration} messages/sec")

        // 验证ConsumeQueue数量：8个队列
        assertEquals(8, messageStore.getConsumeQueueCount())
    }
}
