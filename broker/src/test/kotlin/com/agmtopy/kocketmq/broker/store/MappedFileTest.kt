package com.agmtopy.kocketmq.broker.store

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * MappedFile测试
 */
class MappedFileTest {

    private val testDir = "/tmp/kocketmq/test/mappedfile"
    private lateinit var mappedFile: MappedFile

    @BeforeEach
    fun setUp() {
        // 清理测试目录
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()
    }

    @AfterEach
    fun tearDown() {
        // 清理资源
        mappedFile.destroy()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test create mapped file`() {
        val fileName = "$testDir/test.data"
        mappedFile = MappedFile(fileName, 1024)

        assertTrue(File(fileName).exists())
        assertEquals(1024, mappedFile.getFileSize())
        assertEquals(0L, mappedFile.getFileFromOffset())
        assertFalse(mappedFile.isFull())
    }

    @Test
    fun `test append and read message`() = runBlocking {
        val fileName = "$testDir/test.data"
        mappedFile = MappedFile(fileName, 1024)

        // 写入消息
        val data = ByteBuffer.wrap("Hello, KocketMQ!".toByteArray())
        val result = mappedFile.appendMessage(data)

        assertEquals(AppendMessageStatus.PUT_OK, result.status)
        assertEquals(0L, result.wroteOffset)
        assertEquals("Hello, KocketMQ!".toByteArray().size, result.wroteBytes)

        // 读取消息
        val readBuffer = mappedFile.getMessage(0, result.wroteBytes)
        assertNotNull(readBuffer)

        val readBytes = ByteArray(result.wroteBytes)
        readBuffer!!.get(readBytes)
        assertEquals("Hello, KocketMQ!", String(readBytes))
    }

    @Test
    fun `test append multiple messages`() = runBlocking {
        val fileName = "$testDir/test.data"
        mappedFile = MappedFile(fileName, 1024)

        // 写入多条消息
        for (i in 1..10) {
            val data = ByteBuffer.wrap("Message $i".toByteArray())
            val result = mappedFile.appendMessage(data)

            assertEquals(AppendMessageStatus.PUT_OK, result.status)
        }

        assertEquals(10 * "Message 1".toByteArray().size, mappedFile.wrotePosition())
    }

    @Test
    fun `test file full`() = runBlocking {
        val fileName = "$testDir/test.data"
        val fileSize = 100
        mappedFile = MappedFile(fileName, fileSize)

        // 写入数据直到文件满
        val data = ByteBuffer.wrap("A".repeat(80).toByteArray())
        val result1 = mappedFile.appendMessage(data)
        assertEquals(AppendMessageStatus.PUT_OK, result1.status)
        assertFalse(mappedFile.isFull())

        // 再次写入，应该返回END_OF_FILE
        val result2 = mappedFile.appendMessage(data)
        assertEquals(AppendMessageStatus.END_OF_FILE, result2.status)
        assertTrue(mappedFile.isFull())
    }

    @Test
    fun `test flush`() = runBlocking {
        val fileName = "$testDir/test.data"
        mappedFile = MappedFile(fileName, 1024)

        // 写入数据
        val data = ByteBuffer.wrap("Test flush".toByteArray())
        mappedFile.appendMessage(data)

        // 刷盘
        val success = mappedFile.flush()
        assertTrue(success)
        assertEquals(mappedFile.wrotePosition(), mappedFile.flushedPosition())
    }

    @Test
    fun `test read invalid offset`() {
        val fileName = "$testDir/test.data"
        mappedFile = MappedFile(fileName, 1024)

        // 读取无效偏移量
        val result = mappedFile.getMessage(-1, 10)
        assertNull(result)

        val result2 = mappedFile.getMessage(100, 10)
        assertNull(result2)
    }

    @Test
    fun `test file from offset`() {
        val fileName = "$testDir/100.data"
        mappedFile = MappedFile(fileName, 1024)

        assertEquals(100L, mappedFile.getFileFromOffset())
    }
}
