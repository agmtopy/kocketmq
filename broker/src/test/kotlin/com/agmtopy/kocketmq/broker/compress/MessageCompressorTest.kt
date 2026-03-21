package com.agmtopy.kocketmq.broker.compress

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * MessageCompressor测试
 */
class MessageCompressorTest {

    @Test
    fun `测试压缩大数据`() {
        // 创建大数据（大于阈值）
        val data = ByteArray(2048) { it.toByte() }

        val compressed = MessageCompressor.compress(data)

        // 压缩后应该更小
        assertTrue(compressed.size < data.size)

        // 应该是GZIP格式
        assertTrue(MessageCompressor.isGzipCompressed(compressed))
    }

    @Test
    fun `测试不压缩小数据`() {
        // 创建小数据（小于阈值）
        val data = ByteArray(100) { it.toByte() }

        val compressed = MessageCompressor.compress(data)

        // 小数据不应该压缩
        assertArrayEquals(data, compressed)
        assertFalse(MessageCompressor.isGzipCompressed(compressed))
    }

    @Test
    fun `测试解压缩`() {
        val original = ByteArray(2048) { it.toByte() }
        val compressed = MessageCompressor.compress(original)

        // 解压缩
        val decompressed = MessageCompressor.decompress(compressed)

        // 应该和原始数据一致
        assertArrayEquals(original, decompressed)
    }

    @Test
    fun `测试解压缩非GZIP数据`() {
        val data = ByteArray(100) { it.toByte() }

        // 非GZIP数据应该直接返回
        val result = MessageCompressor.decompress(data)

        assertArrayEquals(data, result)
    }

    @Test
    fun `测试压缩级别NONE`() {
        val data = ByteArray(2048) { it.toByte() }

        val compressed = MessageCompressor.compress(data, MessageCompressor.CompressionLevel.NONE)

        // NONE级别不应该压缩
        assertArrayEquals(data, compressed)
    }

    @Test
    fun `测试压缩率计算`() {
        val ratio = MessageCompressor.calculateCompressionRatio(1000, 500)

        assertEquals(50.0, ratio, 0.01)
    }

    @Test
    fun `测试是否应该压缩`() {
        // 小数据不应该压缩
        assertFalse(MessageCompressor.shouldCompress(100, MessageCompressor.CompressionLevel.BALANCED))

        // 大数据应该压缩
        assertTrue(MessageCompressor.shouldCompress(2048, MessageCompressor.CompressionLevel.BALANCED))

        // NONE级别不应该压缩
        assertFalse(MessageCompressor.shouldCompress(2048, MessageCompressor.CompressionLevel.NONE))
    }

    @Test
    fun `测试GZIP格式检测`() {
        val data = ByteArray(2048) { it.toByte() }
        val compressed = MessageCompressor.compress(data)

        assertTrue(MessageCompressor.isGzipCompressed(compressed))
        assertFalse(MessageCompressor.isGzipCompressed(data))
    }
}
