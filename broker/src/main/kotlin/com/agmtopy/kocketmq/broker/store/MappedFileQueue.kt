package com.agmtopy.kocketmq.broker.store

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * MappedFile队列管理器
 *
 * 管理多个MappedFile，提供文件创建、查找、刷盘等功能。
 * 文件按文件名（起始偏移量）排序。
 */
class MappedFileQueue(
    private val storePath: String,
    private val mappedFileSize: Int
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(MappedFileQueue::class.java)
    }

    // MappedFile列表（线程安全）
    private val mappedFiles = CopyOnWriteArrayList<MappedFile>()

    // 总刷新字节数（用于统计）
    private val totalFlushedBytes = AtomicLong(0)

    // ==================== 初始化 ====================

    /**
     * 加载所有文件
     *
     * @return 是否成功
     */
    suspend fun load(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(storePath)
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    log.info("Create store directory: {}, result: {}", storePath, created)
                    return@withContext true
                }

                val files = dir.listFiles()
                if (files == null || files.isEmpty()) {
                    log.info("No files found in: {}", storePath)
                    return@withContext true
                }

                // 按文件名排序（文件名即起始偏移量）
                val sortedFiles = files
                    .filter { it.isFile && it.length() == mappedFileSize.toLong() }
                    .sortedBy { it.name.toLongOrNull() ?: 0L }

                for (file in sortedFiles) {
                    try {
                        val mappedFile = MappedFile(file.absolutePath, mappedFileSize)
                        mappedFiles.add(mappedFile)
                        log.info("Loaded MappedFile: {}, wrotePosition={}", file.name, mappedFile.wrotePosition())
                    } catch (e: Exception) {
                        log.error("Load MappedFile failed: {}", file.name, e)
                    }
                }

                log.info("Loaded {} MappedFiles from: {}", mappedFiles.size, storePath)
                true
            } catch (e: Exception) {
                log.error("Load MappedFileQueue failed: {}", storePath, e)
                false
            }
        }
    }

    // ==================== 文件管理 ====================

    /**
     * 获取最后一个MappedFile
     *
     * @return 最后一个文件，如果没有返回null
     */
    fun getLastMappedFile(): MappedFile? {
        return mappedFiles.lastOrNull()
    }

    /**
     * 获取或创建最后一个MappedFile
     *
     * @return 最后一个文件
     */
    suspend fun getLastMappedFileOrCreate(): MappedFile? {
        var mappedFile = getLastMappedFile()

        if (mappedFile == null || mappedFile.isFull()) {
            mappedFile = createMappedFile()
        }

        return mappedFile
    }

    /**
     * 创建新的MappedFile
     *
     * @param startOffset 起始偏移量（默认根据现有文件计算）
     * @return 新创建的文件
     */
    suspend fun createMappedFile(startOffset: Long = calculateNextOffset()): MappedFile? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "$storePath/${String.format("%020d", startOffset)}"
                val mappedFile = MappedFile(fileName, mappedFileSize)

                mappedFiles.add(mappedFile)

                log.info("Created new MappedFile: {}", fileName)
                mappedFile
            } catch (e: Exception) {
                log.error("Create MappedFile failed, offset: {}", startOffset, e)
                null
            }
        }
    }

    /**
     * 根据偏移量查找MappedFile
     *
     * @param offset 物理偏移量
     * @return 对应的文件，如果找不到返回null
     */
    fun findMappedFile(offset: Long): MappedFile? {
        if (mappedFiles.isEmpty()) {
            return null
        }

        // 计算文件索引
        val firstFileOffset = mappedFiles[0].fileFromOffset
        if (offset < firstFileOffset) {
            return null
        }

        val fileIndex = ((offset - firstFileOffset) / mappedFileSize).toInt()

        // 边界检查
        if (fileIndex < 0 || fileIndex >= mappedFiles.size) {
            return null
        }

        val mappedFile = mappedFiles[fileIndex]

        // 验证偏移量确实在该文件内
        if (offset >= mappedFile.fileFromOffset &&
            offset < mappedFile.fileFromOffset + mappedFileSize) {
            return mappedFile
        }

        return null
    }

    // ==================== 刷盘操作 ====================

    /**
     * 批量刷盘
     *
     * @param flushLeastPages 最少刷盘页数
     * @return 是否成功
     */
    suspend fun flush(flushLeastPages: Int): Boolean {
        var result = true
        var flushedBytes = 0L

        for (mappedFile in mappedFiles) {
            val flushed = mappedFile.flush(flushLeastPages)
            if (!flushed) {
                result = false
            } else {
                flushedBytes += mappedFile.flushedPosition()
            }
        }

        if (flushedBytes > 0) {
            totalFlushedBytes.addAndGet(flushedBytes)
        }

        return result
    }

    // ==================== 偏移量计算 ====================

    /**
     * 获取最小偏移量
     */
    fun getMinOffset(): Long {
        return mappedFiles.firstOrNull()?.fileFromOffset ?: 0L
    }

    /**
     * 获取最大偏移量
     */
    fun getMaxOffset(): Long {
        val lastFile = getLastMappedFile() ?: return 0L
        return lastFile.fileFromOffset + lastFile.wrotePosition()
    }

    /**
     * 获取总大小
     */
    fun getTotalSize(): Long {
        return mappedFiles.size.toLong() * mappedFileSize
    }

    // ==================== 统计信息 ====================

    /**
     * 获取文件数量
     */
    fun size(): Int = mappedFiles.size

    /**
     * 是否为空
     */
    fun isEmpty(): Boolean = mappedFiles.isEmpty()

    /**
     * 获取已刷盘总字节数
     */
    fun getTotalFlushedBytes(): Long = totalFlushedBytes.get()

    // ==================== 清理操作 ====================

    /**
     * 销毁所有文件
     */
    fun destroy() {
        for (mappedFile in mappedFiles) {
            mappedFile.destroy()
        }
        mappedFiles.clear()
        log.info("Destroyed all MappedFiles in: {}", storePath)
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算下一个文件的起始偏移量
     */
    private fun calculateNextOffset(): Long {
        val lastFile = getLastMappedFile()
        return if (lastFile != null) {
            lastFile.fileFromOffset + mappedFileSize
        } else {
            0L
        }
    }
}
