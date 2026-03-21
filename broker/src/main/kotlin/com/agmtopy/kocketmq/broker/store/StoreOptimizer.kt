package com.agmtopy.kocketmq.broker.store

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 存储优化工具
 *
 * 提供存储相关的性能优化：
 * - 文件预热
 * - 零拷贝优化
 * - 缓冲区管理
 */
object StoreOptimizer {

    private val log: InternalLogger = InternalLoggerFactory.getLogger(StoreOptimizer::class.java)

    /**
     * 文件预热块大小（字节）
     */
    const val WARMUP_BLOCK_SIZE = 1024 * 1024  // 1MB

    /**
     * 预热MappedFile
     *
     * 将文件内容加载到内存，减少首次访问延迟
     *
     * @param mappedFile 要预热的文件
     * @param warmupSize 预热大小（字节），-1表示全部
     */
    suspend fun warmupMappedFile(mappedFile: MappedFile, warmupSize: Int = -1) {
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val fileSize = mappedFile.fileSize
                val warmupBytes = if (warmupSize == -1) fileSize else minOf(warmupSize, fileSize)

                log.info("开始预热文件: ${mappedFile.fileName}, size=${warmupBytes} bytes")

                // 读取每个块的首字节，触发OS预读
                val buffer = ByteBuffer.allocate(1)
                var pos = 0L
                var touched = 0

                while (pos < warmupBytes) {
                    mappedFile.getMessage(pos.toInt(), 1)?.let {
                        touched++
                    }
                    pos += WARMUP_BLOCK_SIZE.toLong()
                }

                val elapsed = System.currentTimeMillis() - startTime

                log.info("文件预热完成: ${mappedFile.fileName}, touched=$touched blocks, time=${elapsed}ms")

            } catch (e: Exception) {
                log.error("文件预热失败: ${mappedFile.fileName}", e)
            }
        }
    }

    /**
     * 零拷贝传输
     *
     * 使用FileChannel.transferTo实现零拷贝
     *
     * @param mappedFile 源文件
     * @param position 起始位置
     * @param size 传输大小
     * @param target 目标通道
     */
    fun zeroCopyTransfer(
        mappedFile: MappedFile,
        position: Int,
        size: Int,
        target: java.nio.channels.WritableByteChannel
    ): Long {
        return try {
            val startTime = System.currentTimeMillis()

            // 使用mappedByteBuffer的transferTo
            // 注意：这里是简化实现，实际应该使用FileChannel.transferTo
            val buffer = mappedFile.getMessage(position, size)
                ?: return 0

            target.write(buffer)

            val elapsed = System.currentTimeMillis() - startTime

            if (elapsed > 10) {
                log.debug("零拷贝传输: size=$size bytes, time=${elapsed}ms")
            }

            size.toLong()

        } catch (e: Exception) {
            log.error("零拷贝传输失败", e)
            0
        }
    }

    /**
     * 优化缓冲区分配
     *
     * 根据数据大小选择合适的缓冲区分配策略：
     * - 小数据：堆内存
     * - 中等数据：直接内存
     * - 大数据：池化直接内存
     */
    fun allocateBuffer(size: Int): ByteBuffer {
        return when {
            size < 1024 -> {
                // 小于1KB，使用堆内存
                ByteBuffer.allocate(size)
            }
            size < 1024 * 1024 -> {
                // 小于1MB，使用直接内存
                ByteBuffer.allocateDirect(size)
            }
            else -> {
                // 大于1MB，使用池化直接内存
                // TODO: 实现缓冲区池
                ByteBuffer.allocateDirect(size)
            }
        }
    }

    /**
     * 计算最优缓冲区大小
     *
     * 根据预期数据量计算最优缓冲区大小
     */
    fun calculateOptimalBufferSize(expectedDataSize: Long): Int {
        // 默认8KB
        val defaultSize = 8 * 1024

        // 如果预期数据量较小，使用预期大小
        if (expectedDataSize < defaultSize) {
            return expectedDataSize.toInt()
        }

        // 如果预期数据量较大，使用默认大小
        // 大数据应该分块处理
        return defaultSize
    }

    /**
     * 判断是否应该使用零拷贝
     *
     * @param dataSize 数据大小
     * @return 是否应该使用零拷贝
     */
    fun shouldUseZeroCopy(dataSize: Int): Boolean {
        // 大于4KB使用零拷贝
        return dataSize >= 4 * 1024
    }

    /**
     * 获取存储性能建议
     *
     * @param commitLogSize CommitLog大小
     * @param consumeQueueSize ConsumeQueue大小
     * @return 性能建议
     */
    fun getPerformanceAdvice(commitLogSize: Long, consumeQueueSize: Long): List<String> {
        val advice = mutableListOf<String>()

        // CommitLog大小建议
        if (commitLogSize > 1024 * 1024 * 1024) {  // > 1GB
            advice.add("CommitLog较大，建议增加文件大小以减少文件切换")
        }

        // ConsumeQueue大小建议
        if (consumeQueueSize > 100 * 1024 * 1024) {  // > 100MB
            advice.add("ConsumeQueue较大，建议增加mappedFileSize")
        }

        // 默认建议
        if (advice.isEmpty()) {
            advice.add("当前存储配置良好")
        }

        return advice
    }
}
