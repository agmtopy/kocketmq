package com.agmtopy.kocketmq.broker.store

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger

/**
 * 内存映射文件
 *
 * 使用FileChannel.map()将文件映射到内存，实现零拷贝读写。
 * 所有写操作通过wrotePosition原子计数器串行化。
 */
class MappedFile(
    val fileName: String,
    val fileSize: Int
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(MappedFile::class.java)

        // 操作系统页大小（4KB）
        const val OS_PAGE_SIZE = 1024 * 4

        // 消息魔数
        const val MESSAGE_MAGIC_CODE = 0xAABBCCDD.toInt()
    }

    // ==================== 文件元信息 ====================

    val file: File = File(fileName)
    val fileFromOffset: Long = try {
        file.name.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        0L
    }

    // ==================== 内存映射 ====================

    private var mappedByteBuffer: MappedByteBuffer? = null
    private var fileChannel: FileChannel? = null
    private var randomAccessFile: RandomAccessFile? = null

    // ==================== 写位置指针 ====================

    // 当前写入位置（原子操作，协程安全）
    private val wrotePosition = AtomicInteger(0)

    // 已刷盘位置
    private val flushedPosition = AtomicInteger(0)

    // 已提交位置
    private val committedPosition = AtomicInteger(0)

    // ==================== 初始化 ====================

    init {
        try {
            // 确保目录存在
            file.parentFile?.mkdirs()

            // 创建RandomAccessFile
            randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile!!.setLength(fileSize.toLong())

            // 获取FileChannel并映射到内存
            fileChannel = randomAccessFile!!.channel
            mappedByteBuffer = fileChannel!!.map(
                FileChannel.MapMode.READ_WRITE,
                0,
                fileSize.toLong()
            )

            log.info("MappedFile created: {}, size={}", fileName, fileSize)
        } catch (e: Exception) {
            log.error("Failed to create MappedFile: {}", fileName, e)
            throw e
        }
    }

    // ==================== 写入操作 ====================

    /**
     * 追加消息到文件
     *
     * @param data 消息字节数据
     * @return 写入结果
     */
    suspend fun appendMessage(data: ByteBuffer): AppendMessageResult {
        return withContext(Dispatchers.IO) {
            // 1. 检查剩余空间
            val currentPos = wrotePosition.get()
            val remaining = fileSize - currentPos

            if (remaining < data.remaining()) {
                return@withContext AppendMessageResult(
                    status = AppendMessageStatus.END_OF_FILE,
                    wroteOffset = -1
                )
            }

            // 2. 写入内存映射缓冲
            val wroteBytes = data.remaining()
            mappedByteBuffer?.let { buffer ->
                buffer.position(currentPos)
                buffer.put(data)
            }

            // 3. 更新写位置
            wrotePosition.addAndGet(wroteBytes)

            // 4. 返回结果
            AppendMessageResult(
                status = AppendMessageStatus.PUT_OK,
                wroteOffset = fileFromOffset + currentPos,
                wroteBytes = wroteBytes
            )
        }
    }

    /**
     * 读取消息
     *
     * @param offset 文件内偏移量
     * @param size 消息大小
     * @return 消息ByteBuffer，如果失败返回null
     */
    fun getMessage(offset: Int, size: Int): ByteBuffer? {
        if (offset < 0 || size < 0) {
            return null
        }

        val currentWrotePos = wrotePosition.get()
        if (offset + size > currentWrotePos) {
            return null
        }

        mappedByteBuffer?.let { buffer ->
            val slice = buffer.slice()
            slice.position(offset)
            slice.limit(offset + size)
            return slice
        }

        return null
    }

    // ==================== 刷盘操作 ====================

    /**
     * 刷盘（强制写入磁盘）
     *
     * @param flushLeastPages 最少刷盘页数（0表示强制刷盘）
     * @return 是否成功
     */
    suspend fun flush(flushLeastPages: Int = 0): Boolean {
        return withContext(Dispatchers.IO) {
            val currentWritePos = wrotePosition.get()
            val currentFlushPos = flushedPosition.get()

            // 计算需要刷盘的页数
            val flushPages = (currentWritePos - currentFlushPos) / OS_PAGE_SIZE

            // 如果页数不够，跳过刷盘
            if (flushPages < flushLeastPages) {
                return@withContext true
            }

            try {
                // 强制刷盘
                mappedByteBuffer?.force()

                // 更新刷盘位置
                flushedPosition.set(currentWritePos)

                log.debug("Flushed MappedFile: {}, position={}", fileName, currentWritePos)
                true
            } catch (e: Exception) {
                log.error("Flush failed: {}", fileName, e)
                false
            }
        }
    }

    // ==================== 状态查询 ====================

    /**
     * 判断文件是否已满
     */
    fun isFull(): Boolean = wrotePosition.get() >= fileSize

    /**
     * 获取当前写入位置
     */
    fun wrotePosition(): Int = wrotePosition.get()

    /**
     * 获取已刷盘位置
     */
    fun flushedPosition(): Int = flushedPosition.get()

    // ==================== 资源清理 ====================

    /**
     * 关闭文件（释放资源）
     */
    fun destroy() {
        try {
            // 关闭FileChannel
            fileChannel?.close()
            fileChannel = null

            // 关闭RandomAccessFile
            randomAccessFile?.close()
            randomAccessFile = null

            // 注意：MappedByteBuffer没有直接的unmap方法
            // 依赖JVM的垃圾回收来清理映射
            mappedByteBuffer = null

            log.info("MappedFile destroyed: {}", fileName)
        } catch (e: Exception) {
            log.error("Destroy MappedFile failed: {}", fileName, e)
        }
    }

    /**
     * 检查文件是否已销毁
     */
    fun isDestroyed(): Boolean = mappedByteBuffer == null
}

// ==================== 数据结构 ====================

/**
 * 追加消息状态
 */
enum class AppendMessageStatus {
    PUT_OK,              // 写入成功
    END_OF_FILE,         // 文件已满
    MESSAGE_ILLEGAL,     // 消息非法
    CREATE_MAPEDFILE_FAILED // 创建映射文件失败
}

/**
 * 追加消息结果
 */
data class AppendMessageResult(
    val status: AppendMessageStatus,
    val wroteOffset: Long,
    val wroteBytes: Int = 0
)
