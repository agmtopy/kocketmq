package com.agmtopy.kocketmq.broker.compress

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 消息压缩工具
 *
 * 支持消息体的压缩和解压缩，减少网络传输和存储空间
 */
object MessageCompressor {

    private val log: InternalLogger = InternalLoggerFactory.getLogger(MessageCompressor::class.java)

    /**
     * 压缩级别
     */
    enum class CompressionLevel {
        NONE,       // 不压缩
        FAST,       // 快速压缩（低压缩率）
        BALANCED,   // 平衡模式（中等压缩率）
        BEST        // 最佳压缩（高压缩率）
    }

    /**
     * 压缩阈值（字节）
     * 小于此值不压缩
     */
    const val COMPRESSION_THRESHOLD = 1024  // 1KB

    /**
     * 压缩消息体
     *
     * @param data 原始数据
     * @param level 压缩级别
     * @return 压缩后的数据，如果数据太小或压缩失败则返回原数据
     */
    fun compress(data: ByteArray, level: CompressionLevel = CompressionLevel.BALANCED): ByteArray {
        // 如果数据太小，不压缩
        if (data.size < COMPRESSION_THRESHOLD) {
            return data
        }

        // 如果选择不压缩
        if (level == CompressionLevel.NONE) {
            return data
        }

        return try {
            val startTime = System.currentTimeMillis()
            val outputStream = ByteArrayOutputStream()

            // 使用GZIP压缩
            GZIPOutputStream(outputStream).use { gzip ->
                gzip.write(data)
            }

            val compressed = outputStream.toByteArray()
            val elapsed = System.currentTimeMillis() - startTime

            // 如果压缩后更大，返回原数据
            if (compressed.size >= data.size) {
                log.debug("压缩后大小增加，放弃压缩: original={}, compressed={}",
                    data.size, compressed.size)
                return data
            }

            val ratio = (1 - compressed.size.toDouble() / data.size) * 100

            log.debug("压缩成功: original={} bytes, compressed={} bytes, ratio={}%, time={}ms",
                data.size, compressed.size, String.format("%.2f", ratio), elapsed)

            compressed

        } catch (e: Exception) {
            log.error("压缩失败，返回原数据", e)
            data
        }
    }

    /**
     * 解压缩消息体
     *
     * @param data 压缩数据
     * @return 解压后的数据，如果解压失败则返回原数据
     */
    fun decompress(data: ByteArray): ByteArray {
        return try {
            // 检查是否是GZIP格式
            if (!isGzipCompressed(data)) {
                return data
            }

            val startTime = System.currentTimeMillis()
            val inputStream = ByteArrayInputStream(data)
            val outputStream = ByteArrayOutputStream()

            GZIPInputStream(inputStream).use { gzip ->
                val buffer = ByteArray(1024)
                var len: Int
                while (gzip.read(buffer).also { len = it } != -1) {
                    outputStream.write(buffer, 0, len)
                }
            }

            val decompressed = outputStream.toByteArray()
            val elapsed = System.currentTimeMillis() - startTime

            log.debug("解压成功: compressed={} bytes, decompressed={} bytes, time={}ms",
                data.size, decompressed.size, elapsed)

            decompressed

        } catch (e: Exception) {
            log.error("解压失败，返回原数据", e)
            data
        }
    }

    /**
     * 检查数据是否被GZIP压缩
     *
     * GZIP魔术数：0x1f 0x8b
     */
    fun isGzipCompressed(data: ByteArray): Boolean {
        return data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
    }

    /**
     * 计算压缩率
     *
     * @param original 原始大小
     * @param compressed 压缩后大小
     * @return 压缩率（百分比）
     */
    fun calculateCompressionRatio(original: Int, compressed: Int): Double {
        if (original == 0) return 0.0
        return (1 - compressed.toDouble() / original) * 100
    }

    /**
     * 判断是否应该压缩
     *
     * @param dataSize 数据大小
     * @param level 压缩级别
     * @return 是否应该压缩
     */
    fun shouldCompress(dataSize: Int, level: CompressionLevel): Boolean {
        return level != CompressionLevel.NONE && dataSize >= COMPRESSION_THRESHOLD
    }
}
