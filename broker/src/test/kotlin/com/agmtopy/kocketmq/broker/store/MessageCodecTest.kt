package com.agmtopy.kocketmq.broker.store

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

/**
 * MessageCodec测试
 */
class MessageCodecTest {

    @Test
    fun `test encode and decode simple message`() {
        val message = MessageExt(
            topic = "TestTopic",
            queueId = 0,
            body = "Hello, KocketMQ!".toByteArray(),
            bodyCRC = MessageExt.calculateCRC32("Hello, KocketMQ!".toByteArray()
        )

        // 编码
        val buffer = MessageCodec.encode(message)

        assertNotNull(buffer)
        assertTrue(buffer.remaining() > 0)

        // 解码
        val decoded = MessageCodec.decode(buffer)

        assertNotNull(decoded)
        assertEquals("TestTopic", decoded!!.topic)
        assertEquals(0, decoded.queueId)
        assertEquals("Hello, KocketMQ!", String(decoded.body))
        assertEquals(message.bodyCRC, decoded.bodyCRC)
    }

    @Test
    fun `test encode and decode message with properties`() {
        val message = MessageExt(
            topic = "TestTopic",
            queueId = 1,
            body = "Message with properties".toByteArray(),
            properties = "TAGS=TagA;KEYS=Order123",
            flag = 1,
            sysFlag = 2,
            queueOffset = 100L,
            bodyCRC = MessageExt.calculateCRC32("Message with properties".toByteArray()
        )

        // 编码
        val buffer = MessageCodec.encode(message)

        // 解码
        val decoded = MessageCodec.decode(buffer)

        assertNotNull(decoded)
        assertEquals("TestTopic", decoded!!.topic)
        assertEquals(1, decoded.queueId)
        assertEquals("Message with properties", String(decoded.body))
        assertEquals("TAGS=TagA;KEYS=Order123", decoded.properties)
        assertEquals(1, decoded.flag)
        assertEquals(2, decoded.sysFlag)
        assertEquals(100L, decoded.queueOffset)
    }

    @Test
    fun `test encode empty message`() {
        val message = MessageExt(
            topic = "EmptyTopic",
            queueId = 0,
            body = ByteArray(0)
        )

        // 编码
        val buffer = MessageCodec.encode(message)

        // 解码
        val decoded = MessageCodec.decode(buffer)

        assertNotNull(decoded)
        assertEquals("EmptyTopic", decoded!!.topic)
        assertEquals(0, decoded.body.size)
    }

    @Test
    fun `test decode invalid magic code`() {
        val buffer = ByteBuffer.allocate(100)

        // 写入错误的魔数
        buffer.putInt(50)  // totalSize
        buffer.putInt(0x12345678)  // 错误的魔数
        buffer.flip()

        // 解码应该返回null
        val decoded = MessageCodec.decode(buffer)
        assertNull(decoded)
    }

    @Test
    fun `test calculate CRC32`() {
        val data1 = "Hello, World!".toByteArray()
        val data2 = "Hello, World!".toByteArray()

        val crc1 = MessageExt.calculateCRC32(data1)
        val crc2 = MessageExt.calculateCRC32(data2)

        // 相同数据应该产生相同的CRC
        assertEquals(crc1, crc2)

        // 不同数据应该产生不同的CRC
        val data3 = "Hello, KocketMQ!".toByteArray()
        val crc3 = MessageExt.calculateCRC32(data3)
        assertNotEquals(crc1, crc3)
    }

    @Test
    fun `test CRC32 verification`() {
        val body = "Test message body".toByteArray()
        val correctCRC = MessageExt.calculateCRC32(body)

        val message = MessageExt(
            topic = "TestTopic",
            queueId = 0,
            body = body,
            bodyCRC = correctCRC
        )

        // 编码
        val buffer = MessageCodec.encode(message)

        // 解码（CRC应该匹配）
        val decoded = MessageCodec.decode(buffer)

        assertNotNull(decoded)
        assertEquals(correctCRC, decoded!!.bodyCRC)
    }

    @Test
    fun `test message size calculation`() {
        val message = MessageExt(
            topic = "TestTopic",
            queueId = 0,
            body = "Test".toByteArray(),
            properties = "KEY=value"
        )

        val calculatedSize = MessageCodec.calTotalSize(message)
        val buffer = MessageCodec.encode(message)
        val totalSize = buffer.remaining() - 4  // 减去totalSize字段

        assertEquals(calculatedSize, totalSize)
    }

    @Test
    fun `test large message`() {
        // 创建大消息（1MB）
        val largeBody = ByteArray(1024 * 1024) { it.toByte() }
        val message = MessageExt(
            topic = "LargeTopic",
            queueId = 0,
            body = largeBody,
            bodyCRC = MessageExt.calculateCRC32(largeBody)
        )

        // 编码
        val buffer = MessageCodec.encode(message)
        assertTrue(buffer.remaining() > 1024 * 1024)

        // 解码
        val decoded = MessageCodec.decode(buffer)

        assertNotNull(decoded)
        assertEquals(largeBody.size, decoded!!.body.size)
        assertTrue(largeBody.contentEquals(decoded.body))
    }

    @Test
    fun `test message with unicode properties`() {
        val message = MessageExt(
            topic = "UnicodeTopic",
            queueId = 0,
            body = "Test".toByteArray(),
            properties = "TAGS=标签;KEYS=键值"
        )

        // 编码
        val buffer = MessageCodec.encode(message)

        // 解码
        val decoded = MessageCodec.decode(buffer)

        assertNotNull(decoded)
        assertEquals("TAGS=标签;KEYS=键值", decoded!!.properties)
    }

    @Test
    fun `test multiple encode decode cycles`() {
        val original = MessageExt(
            topic = "CycleTest",
            queueId = 5,
            body = "Cycle test message".toByteArray(),
            properties = "CYCLE=true",
            flag = 123,
            sysFlag = 456,
            queueOffset = 789L
        )

        // 多次编码解码
        var current = original
        for (i in 1..5) {
            val buffer = MessageCodec.encode(current)
            current = MessageCodec.decode(buffer)!!
        }

        // 验证数据仍然正确
        assertEquals(original.topic, current.topic)
        assertEquals(original.queueId, current.queueId)
        assertEquals(String(original.body), String(current.body))
        assertEquals(original.properties, current.properties)
        assertEquals(original.flag, current.flag)
        assertEquals(original.sysFlag, current.sysFlag)
        assertEquals(original.queueOffset, current.queueOffset)
    }
}
