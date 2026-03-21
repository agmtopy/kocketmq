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
 * CommitLogActor测试
 */
class CommitLogActorTest {

    private val testDir = "/tmp/kocketmq/test/commitlog"
    private val fileSize = 1024 * 10  // 10KB，便于测试文件满
    private lateinit var commitLog: CommitLogActor

    @BeforeEach
    fun setUp() {
        // 清理测试目录
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()
    }

    @AfterEach
    fun tearDown(): Unit = runBlocking {
        // 清理资源
        commitLog.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test load empty directory`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        val loaded = commitLog.load()

        assertTrue(loaded)
        assertEquals(0, commitLog.getFileCount())
        assertEquals(0L, commitLog.getMinOffset())
        assertEquals(0L, commitLog.getMaxOffset())
    }

    @Test
    fun `test start and shutdown`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())

        // 启动
        commitLog.start()
        // 给协程启动时间
        Thread.sleep(100)

        // 关闭
        commitLog.shutdown()

        // 应该能正常完成
        assertTrue(true)
    }

    @Test
    fun `test append and get message`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        // 创建消息
        val message = MessageExt(
            topic = "TestTopic",
            queueId = 0,
            body = "Hello, CommitLog!".toByteArray(),
            bodyCRC = MessageExt.calculateCRC32("Hello, CommitLog!".toByteArray())
        )

        // 追加消息
        val result = commitLog.appendMessage(message)

        assertEquals(AppendMessageStatus.PUT_OK, result.status)
        assertTrue(result.wroteOffset >= 0)
        assertTrue(result.wroteBytes > 0)

        // 读取消息
        val readMessage = commitLog.getMessage(result.wroteOffset)

        assertNotNull(readMessage)
        assertEquals("TestTopic", readMessage!!.topic)
        assertEquals(0, readMessage.queueId)
        assertEquals("Hello, CommitLog!", String(readMessage.body))
    }

    @Test
    fun `test append multiple messages`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        // 追加10条消息
        val results = mutableListOf<AppendMessageResult>()
        for (i in 1..10) {
            val message = MessageExt(
                topic = "Topic$i",
                queueId = i % 3,
                body = "Message $i".toByteArray(),
                bodyCRC = MessageExt.calculateCRC32("Message $i".toByteArray())
            )

            val result = commitLog.appendMessage(message)
            assertEquals(AppendMessageStatus.PUT_OK, result.status)
            results.add(result)
        }

        // 验证offset递增
        for (i in 1 until results.size) {
            assertTrue(results[i].wroteOffset > results[i - 1].wroteOffset)
        }

        // 验证所有消息都能读取
        for ((index, result) in results.withIndex()) {
            val message = commitLog.getMessage(result.wroteOffset)
            assertNotNull(message)
            assertEquals("Topic${index + 1}", message!!.topic)
            assertEquals("Message ${index + 1}", String(message.body))
        }
    }

    @Test
    fun `test concurrent append`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        // 并发追加100条消息
        val jobs = (1..100).map { i ->
            async {
                val message = MessageExt(
                    topic = "ConcurrentTest",
                    queueId = 0,
                    body = "Message $i".toByteArray()
                )
                commitLog.appendMessage(message)
            }
        }

        val results = jobs.awaitAll()

        // 所有消息应该成功写入
        assertTrue(results.all { it.status == AppendMessageStatus.PUT_OK })

        // maxOffset应该正确更新
        assertTrue(commitLog.getMaxOffset() > 0)
    }

    @Test
    fun `test file full and create new`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        // 写入数据直到文件满
        var fileCount = 1
        var totalMessages = 0

        while (fileCount == 1 && totalMessages < 1000) {
            val message = MessageExt(
                topic = "FillTest",
                queueId = 0,
                body = "A".repeat(100).toByteArray()
            )

            val result = commitLog.appendMessage(message)
            assertEquals(AppendMessageStatus.PUT_OK, result.status)

            totalMessages++
            fileCount = commitLog.getFileCount()
        }

        // 应该创建了新文件
        assertTrue(commitLog.getFileCount() > 1)
        println("Created ${commitLog.getFileCount()} files after $totalMessages messages")
    }

    @Test
    fun `test flush`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        // 追加消息
        val message = MessageExt(
            topic = "FlushTest",
            queueId = 0,
            body = "Test flush".toByteArray()
        )
        commitLog.appendMessage(message)

        // 刷盘
        val success = commitLog.flush(0)
        assertTrue(success)

        // 验证刷盘偏移量
        assertTrue(commitLog.getFlushedOffset() > 0)
    }

    @Test
    fun `test get min and max offset`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        // 初始状态
        assertEquals(0L, commitLog.getMinOffset())
        assertEquals(0L, commitLog.getMaxOffset())

        // 追加消息
        for (i in 1..5) {
            val message = MessageExt(
                topic = "OffsetTest",
                queueId = 0,
                body = "Message $i".toByteArray()
            )
            commitLog.appendMessage(message)
        }

        // 验证offset
        assertEquals(0L, commitLog.getMinOffset())
        assertTrue(commitLog.getMaxOffset() > 0)
    }

    @Test
    fun `test load existing files`() = runBlocking {
        // 创建第一个CommitLog并写入消息
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        val message1 = MessageExt(
            topic = "ExistingTest",
            queueId = 0,
            body = "First message".toByteArray()
        )
        val result1 = commitLog.appendMessage(message1)

        // 刷盘
        commitLog.flush(0)

        // 关闭
        commitLog.shutdown()

        // 重新加载
        val commitLog2 = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog2.load())
        commitLog2.start()

        // 验证offset恢复
        assertEquals(result1.wroteOffset + result1.wroteBytes, commitLog2.getMaxOffset())

        // 读取消息
        val message = commitLog2.getMessage(result1.wroteOffset)
        assertNotNull(message)
        assertEquals("First message", String(message!!.body))

        commitLog2.shutdown()
    }

    @Test
    fun `test read non-existent offset`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        // 读取不存在的offset
        val message = commitLog.getMessage(999999L)
        assertNull(message)
    }

    @Test
    fun `test large message`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        // 创建大消息（1KB）
        val largeBody = ByteArray(1024) { it.toByte() }
        val message = MessageExt(
            topic = "LargeTest",
            queueId = 0,
            body = largeBody,
            bodyCRC = MessageExt.calculateCRC32(largeBody)
        )

        // 追加
        val result = commitLog.appendMessage(message)
        assertEquals(AppendMessageStatus.PUT_OK, result.status)

        // 读取
        val read = commitLog.getMessage(result.wroteOffset)
        assertNotNull(read)
        assertTrue(largeBody.contentEquals(read!!.body))
    }

    @Test
    fun `test message with properties`() = runBlocking {
        commitLog = CommitLogActor(testDir, fileSize)
        assertTrue(commitLog.load())
        commitLog.start()

        val message = MessageExt(
            topic = "PropertiesTest",
            queueId = 0,
            body = "Test".toByteArray(),
            properties = "TAGS=TagA;KEYS=Key123",
            flag = 1,
            sysFlag = 2,
            queueOffset = 100L
        )

        val result = commitLog.appendMessage(message)
        assertEquals(AppendMessageStatus.PUT_OK, result.status)

        val read = commitLog.getMessage(result.wroteOffset)
        assertNotNull(read)
        assertEquals("TAGS=TagA;KEYS=Key123", read!!.properties)
        assertEquals(1, read.flag)
        assertEquals(2, read.sysFlag)
        assertEquals(100L, read.queueOffset)
    }
}
