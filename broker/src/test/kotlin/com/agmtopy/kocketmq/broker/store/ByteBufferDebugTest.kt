package com.agmtopy.kocketmq.broker.store

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class ByteBufferDebugTest {

    @Test
    fun `debug byte buffer slice`(): Unit = runBlocking {
        // 模拟MappedFile的行为
        val buffer = ByteBuffer.allocate(1024)

        // 写入数据
        val data = ByteBuffer.wrap("Hello, KocketMQ!".toByteArray())
        val wroteBytes = data.remaining()
        buffer.position(0)
        buffer.put(data)

        println("After write - buffer position: ${buffer.position()}, limit: ${buffer.limit()}")

        // 使用正确的方法：duplicate()
        val readBuffer = buffer.duplicate()
        println("After duplicate - readBuffer position: ${readBuffer.position()}, limit: ${readBuffer.limit()}")

        readBuffer.position(0)
        readBuffer.limit(wroteBytes)
        println("After setting position/limit - readBuffer position: ${readBuffer.position()}, limit: ${readBuffer.limit()}")

        val readBytes = ByteArray(wroteBytes)
        readBuffer.get(readBytes)
        val result = String(readBytes)

        println("Read result: $result")
        assert(result == "Hello, KocketMQ!") { "Expected: Hello, KocketMQ!, but was: $result" }
    }
}
