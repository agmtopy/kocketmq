package com.agmtopy.kocketmq.broker.store

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * MappedFileQueue测试
 */
class MappedFileQueueTest {

    private val testDir = "/tmp/kocketmq/test/mappedfilequeue"
    private val fileSize = 1024
    private lateinit var queue: MappedFileQueue

    @BeforeEach
    fun setUp() {
        // 清理测试目录
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()
    }

    @AfterEach
    fun tearDown() {
        // 清理资源
        queue.destroy()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test load empty directory`() = runBlocking {
        queue = MappedFileQueue(testDir, fileSize)
        val result = queue.load()

        assertTrue(result)
        assertEquals(0, queue.size())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `test create mapped file`() = runBlocking {
        queue = MappedFileQueue(testDir, fileSize)
        queue.load()

        val file = queue.createMappedFile(0L)

        assertNotNull(file)
        assertEquals(0L, file!!.getFileFromOffset())
        assertEquals(1, queue.size())
        assertFalse(queue.isEmpty())
    }

    @Test
    fun `test get last mapped file`() = runBlocking {
        queue = MappedFileQueue(testDir, fileSize)
        queue.load()

        // 没有文件时返回null
        assertNull(queue.getLastMappedFile())

        // 创建文件
        queue.createMappedFile(0L)
        val lastFile = queue.getLastMappedFile()

        assertNotNull(lastFile)
        assertEquals(0L, lastFile!!.getFileFromOffset())
    }

    @Test
    fun `test find mapped file`() = runBlocking {
        queue = MappedFileQueue(testDir, fileSize)
        queue.load()

        // 创建3个文件：0, 1024, 2048
        queue.createMappedFile(0L)
        queue.createMappedFile(1024L)
        queue.createMappedFile(2048L)

        // 查找偏移量512 -> 第一个文件
        val file1 = queue.findMappedFile(512)
        assertNotNull(file1)
        assertEquals(0L, file1!!.getFileFromOffset())

        // 查找偏移量1500 -> 第二个文件
        val file2 = queue.findMappedFile(1500)
        assertNotNull(file2)
        assertEquals(1024L, file2!!.getFileFromOffset())

        // 查找偏移量2100 -> 第三个文件
        val file3 = queue.findMappedFile(2100)
        assertNotNull(file3)
        assertEquals(2048L, file3!!.getFileFromOffset())

        // 查找超出范围的偏移量
        val file4 = queue.findMappedFile(10000)
        assertNull(file4)
    }

    @Test
    fun `test append and read across files`() = runBlocking {
        queue = MappedFileQueue(testDir, fileSize)
        queue.load()

        // 写入数据填满第一个文件
        val file1 = queue.createMappedFile(0L)!!
        val data1 = ByteBuffer.wrap("A".repeat(1000).toByteArray())
        val result1 = file1.appendMessage(data1)

        assertEquals(AppendMessageStatus.PUT_OK, result1.status)

        // 再次写入，第一个文件已满，应该返回END_OF_FILE
        val data2 = ByteBuffer.wrap("B".repeat(100).toByteArray())
        val result2 = file1.appendMessage(data2)
        assertEquals(AppendMessageStatus.END_OF_FILE, result2.status)

        // 创建第二个文件
        val file2 = queue.createMappedFile(1024L)!!
        val result3 = file2.appendMessage(data2)
        assertEquals(AppendMessageStatus.PUT_OK, result3.status)

        // 读取第一个文件
        val read1 = file1.getMessage(0, 100)
        assertNotNull(read1)
        assertEquals("A".repeat(100), String(read1!!.array(), 0, 100))

        // 读取第二个文件
        val read2 = file2.getMessage(0, 100)
        assertNotNull(read2)
        assertEquals("B".repeat(100), String(read2!!.array(), 0, 100))
    }

    @Test
    fun `test get min and max offset`() = runBlocking {
        queue = MappedFileQueue(testDir, fileSize)
        queue.load()

        // 没有文件时
        assertEquals(0L, queue.getMinOffset())
        assertEquals(0L, queue.getMaxOffset())

        // 创建文件并写入数据
        val file1 = queue.createMappedFile(0L)!!
        file1.appendMessage(ByteBuffer.wrap("Test".toByteArray()))

        assertEquals(0L, queue.getMinOffset())
        assertEquals(4L, queue.getMaxOffset())

        // 创建第二个文件
        val file2 = queue.createMappedFile(1024L)!!
        file2.appendMessage(ByteBuffer.wrap("Test2".toByteArray()))

        assertEquals(0L, queue.getMinOffset())
        assertEquals(1029L, queue.getMaxOffset())  // 1024 + 5
    }

    @Test
    fun `test flush`() = runBlocking {
        queue = MappedFileQueue(testDir, fileSize)
        queue.load()

        // 创建文件并写入数据
        val file1 = queue.createMappedFile(0L)!!
        file1.appendMessage(ByteBuffer.wrap("Test1".toByteArray()))

        val file2 = queue.createMappedFile(1024L)!!
        file2.appendMessage(ByteBuffer.wrap("Test2".toByteArray()))

        // 批量刷盘
        val success = queue.flush(0)
        assertTrue(success)

        // 验证刷盘位置
        assertEquals(5, file1.flushedPosition())
        assertEquals(5, file2.flushedPosition())
    }

    @Test
    fun `test load existing files`() = runBlocking {
        // 先创建一些文件
        queue = MappedFileQueue(testDir, fileSize)
        queue.load()

        queue.createMappedFile(0L)
        queue.createMappedFile(1024L)
        queue.createMappedFile(2048L)

        // 写入一些数据
        queue.findMappedFile(0)?.appendMessage(ByteBuffer.wrap("Test".toByteArray()))
        queue.findMappedFile(1024)?.appendMessage(ByteBuffer.wrap("Test2".toByteArray()))

        // 销毁队列
        queue.destroy()

        // 重新加载
        val queue2 = MappedFileQueue(testDir, fileSize)
        val loaded = queue2.load()

        assertTrue(loaded)
        assertEquals(3, queue2.size())
        assertEquals(0L, queue2.getMinOffset())

        // 清理
        queue2.destroy()
    }

    @Test
    fun `test destroy`() = runBlocking {
        queue = MappedFileQueue(testDir, fileSize)
        queue.load()

        // 创建文件
        queue.createMappedFile(0L)
        queue.createMappedFile(1024L)

        assertEquals(2, queue.size())

        // 销毁
        queue.destroy()

        assertEquals(0, queue.size())
        assertTrue(queue.isEmpty())
    }
}
