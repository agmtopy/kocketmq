package com.agmtopy.kocketmq.broker.store

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * ConsumeQueue - 单个队列的索引文件
 *
 * 存储消息在CommitLog中的物理偏移量，按逻辑offset组织。
 * 每个索引单元20字节：[phyOffset(8)][size(4)][tagsCode(8)]
 */
class ConsumeQueue(
    private val storePath: String,
    private val topic: String,
    private val queueId: Int,
    private val mappedFileSize: Int = 1024 * 1024 * 6  // 默认6MB
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(ConsumeQueue::class.java)

        // 索引单元大小：offset(8) + size(4) + tagsCode(8) = 20字节
        const val CQ_STORE_UNIT_SIZE = 20
    }

    // 队列目录
    private val queueDir: String = buildQueueDir(storePath, topic, queueId)

    // MappedFile队列
    private val mappedFileQueue = MappedFileQueue(queueDir, mappedFileSize)

    // 最大物理偏移量
    private var maxPhysicOffset = -1L

    // 最小逻辑偏移量
    private var minLogicOffset = 0L

    /**
     * 构建队列目录路径
     */
    private fun buildQueueDir(storePath: String, topic: String, queueId: Int): String {
        return "$storePath${File.separator}consumequeue${File.separator}$topic${File.separator}$queueId"
    }

    /**
     * 加载ConsumeQueue
     */
    suspend fun load(): Boolean {
        return withContext(Dispatchers.IO) {
            val loaded = mappedFileQueue.load()
            if (loaded && mappedFileQueue.size() > 0) {
                // 恢复maxPhysicOffset（从最后一个索引单元读取）
                // 简化实现：使用文件的最大偏移量
                maxPhysicOffset = mappedFileQueue.getMaxOffset()
            }
            loaded
        }
    }

    /**
     * 追加索引单元
     *
     * @param phyOffset 物理偏移量
     * @param size 消息大小
     * @param tagsCode 标签哈希码（用于过滤）
     */
    suspend fun append(phyOffset: Long, size: Int, tagsCode: Long = 0) {
        withContext(Dispatchers.IO) {
            // 1. 获取或创建MappedFile
            var mappedFile = mappedFileQueue.getLastMappedFile()

            if (mappedFile == null || mappedFile.isFull()) {
                mappedFile = mappedFileQueue.createMappedFile()
            }

            // 2. 编码索引单元（20字节）
            val buffer = ByteBuffer.allocate(CQ_STORE_UNIT_SIZE)
            buffer.putLong(phyOffset)
            buffer.putInt(size)
            buffer.putLong(tagsCode)
            buffer.flip()

            // 3. 写入
            val result = mappedFile.appendMessage(buffer)

            if (result.status == AppendMessageStatus.PUT_OK) {
                maxPhysicOffset = phyOffset
            } else {
                log.error("Append ConsumeQueue failed: topic={}, queueId={}", topic, queueId)
            }
        }
    }

    /**
     * 根据逻辑offset获取物理offset
     *
     * @param logicOffset 逻辑偏移量（第几条消息）
     * @return 物理偏移量，失败返回-1
     */
    fun getIndex(logicOffset: Long): Long {
        if (logicOffset < 0) {
            return -1
        }

        // 计算文件内偏移
        val fileOffset = logicOffset * CQ_STORE_UNIT_SIZE

        // 定位MappedFile
        val mappedFile = mappedFileQueue.findMappedFile(fileOffset)
            ?: return -1

        // 计算文件内具体位置
        val position = (fileOffset - mappedFile.getFileFromOffset()).toInt()

        // 检查边界
        if (position < 0 || position >= mappedFile.wrotePosition()) {
            return -1
        }

        // 读取物理偏移量（前8字节）
        val buffer = mappedFile.getMessage(position, 8)
            ?: return -1

        return buffer.long
    }

    /**
     * 批量获取物理偏移量
     *
     * @param startLogicOffset 起始逻辑偏移量
     * @param count 数量
     * @return 物理偏移量列表
     */
    fun getPhysicOffsets(startLogicOffset: Long, count: Int): List<Long> {
        val offsets = mutableListOf<Long>()

        for (i in 0 until count) {
            val offset = getIndex(startLogicOffset + i)
            if (offset < 0) break
            offsets.add(offset)
        }

        return offsets
    }

    /**
     * 刷盘
     */
    suspend fun flush() {
        mappedFileQueue.flush(0)
    }

    /**
     * 获取最小逻辑offset
     */
    fun getMinOffsetInQueue(): Long {
        return minLogicOffset
    }

    /**
     * 获取最大逻辑offset
     */
    fun getMaxOffsetInQueue(): Long {
        val lastFile = mappedFileQueue.getLastMappedFile() ?: return 0L
        return lastFile.wrotePosition() / CQ_STORE_UNIT_SIZE
    }

    /**
     * 获取消息总数量
     */
    fun getMessageTotal(): Long {
        return getMaxOffsetInQueue()
    }

    /**
     * 获取Topic
     */
    fun getTopic(): String = topic

    /**
     * 获取QueueId
     */
    fun getQueueId(): Int = queueId

    /**
     * 销毁
     */
    fun destroy() {
        mappedFileQueue.destroy()
    }
}
