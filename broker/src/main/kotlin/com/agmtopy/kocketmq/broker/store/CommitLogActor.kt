package com.agmtopy.kocketmq.broker.store

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * CommitLog Actor
 *
 * 负责消息的顺序写入和读取，是存储引擎的核心组件。
 * 使用Actor模型，通过Channel接收请求，单协程串行处理。
 */
class CommitLogActor(
    private val storePath: String,
    private val mappedFileSize: Int = 1024 * 1024 * 1024  // 默认1GB
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(CommitLogActor::class.java)
    }

    // ==================== 内部数据结构 ====================

    /**
     * 追加请求
     */
    private data class AppendRequest(
        val message: MessageExt,
        val deferred: CompletableDeferred<AppendMessageResult>
    )

    /**
     * 读取请求
     */
    private data class GetRequest(
        val phyOffset: Long,
        val deferred: CompletableDeferred<MessageExt?>
    )

    /**
     * 刷盘请求
     */
    private data class FlushRequest(
        val flushLeastPages: Int,
        val deferred: CompletableDeferred<Boolean>
    )

    // ==================== 状态管理 ====================

    // 请求Channel（无界缓冲）
    private val appendChannel = Channel<AppendRequest>(Channel.UNLIMITED)
    private val getChannel = Channel<GetRequest>(Channel.UNLIMITED)
    private val flushChannel = Channel<FlushRequest>(Channel.UNLIMITED)

    // MappedFile队列
    private val mappedFileQueue = MappedFileQueue(
        storePath + File.separator + "commitlog",
        mappedFileSize
    )

    // 最大偏移量
    private val _maxOffset = MutableStateFlow(0L)
    val maxOffset: StateFlow<Long> = _maxOffset.asStateFlow()

    // 处理协程
    private var appendJob: Job? = null
    private var getJob: Job? = null
    private var flushJob: Job? = null

    // ==================== 生命周期管理 ====================

    /**
     * 加载CommitLog
     */
    suspend fun load(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val loaded = mappedFileQueue.load()
                if (!loaded) {
                    log.error("Load CommitLog failed")
                    return@withContext false
                }

                // 恢复maxOffset
                _maxOffset.value = mappedFileQueue.getMaxOffset()

                log.info("CommitLog loaded, files={}, maxOffset={}",
                    mappedFileQueue.size(), _maxOffset.value)

                true
            } catch (e: Exception) {
                log.error("Load CommitLog exception", e)
                false
            }
        }
    }

    /**
     * 启动Actor
     */
    fun start() {
        // 启动追加消息协程（单个协程，串行处理）
        appendJob = CoroutineScope(Dispatchers.IO).launch {
            log.info("CommitLogActor append coroutine started")

            for (request in appendChannel) {
                try {
                    val result = processAppend(request.message)
                    request.deferred.complete(result)
                } catch (e: Exception) {
                    log.error("Process append failed", e)
                    request.deferred.completeExceptionally(e)
                }
            }
        }

        // 启动读取消息协程
        getJob = CoroutineScope(Dispatchers.IO).launch {
            log.info("CommitLogActor get coroutine started")

            for (request in getChannel) {
                try {
                    val message = processGet(request.phyOffset)
                    request.deferred.complete(message)
                } catch (e: Exception) {
                    log.error("Process get failed", e)
                    request.deferred.completeExceptionally(e)
                }
            }
        }

        // 启动刷盘协程
        flushJob = CoroutineScope(Dispatchers.IO).launch {
            log.info("CommitLogActor flush coroutine started")

            for (request in flushChannel) {
                try {
                    val success = mappedFileQueue.flush(request.flushLeastPages)
                    request.deferred.complete(success)
                } catch (e: Exception) {
                    log.error("Process flush failed", e)
                    request.deferred.completeExceptionally(e)
                }
            }
        }

        log.info("CommitLogActor started")
    }

    /**
     * 关闭Actor
     */
    suspend fun shutdown() {
        log.info("CommitLogActor shutting down...")

        // 取消协程
        appendJob?.cancelAndJoin()
        getJob?.cancelAndJoin()
        flushJob?.cancelAndJoin()

        // 销毁文件队列
        mappedFileQueue.destroy()

        log.info("CommitLogActor shutdown complete")
    }

    // ==================== 消息操作 ====================

    /**
     * 追加消息（suspend接口）
     */
    suspend fun appendMessage(message: MessageExt): AppendMessageResult {
        val request = AppendRequest(message, CompletableDeferred())
        appendChannel.send(request)
        return request.deferred.await()
    }

    /**
     * 处理追加消息（串行处理，无需锁）
     */
    private suspend fun processAppend(message: MessageExt): AppendMessageResult {
        // 1. 获取或创建MappedFile
        var mappedFile = mappedFileQueue.getLastMappedFile()

        if (mappedFile == null || mappedFile.isFull()) {
            // 创建新文件
            mappedFile = mappedFileQueue.createMappedFile(_maxOffset.value)
            if (mappedFile == null) {
                log.error("Create mapped file failed")
                return AppendMessageResult(
                    AppendMessageStatus.CREATE_MAPEDFILE_FAILED,
                    -1
                )
            }
        }

        // 2. 编码消息
        val messageBuffer = MessageCodec.encode(message)

        // 3. 写入文件
        val result = mappedFile.appendMessage(messageBuffer)

        // 4. 更新maxOffset
        if (result.status == AppendMessageStatus.PUT_OK) {
            _maxOffset.value = result.wroteOffset + result.wroteBytes
        }

        return result
    }

    /**
     * 根据物理偏移量读取消息
     */
    suspend fun getMessage(phyOffset: Long): MessageExt? {
        val request = GetRequest(phyOffset, CompletableDeferred())
        getChannel.send(request)
        return request.deferred.await()
    }

    /**
     * 处理读取消息
     */
    private fun processGet(phyOffset: Long): MessageExt? {
        // 1. 根据偏移量定位MappedFile
        val mappedFile = mappedFileQueue.findMappedFile(phyOffset)
            ?: return null

        // 2. 计算文件内偏移
        val fileOffset = (phyOffset - mappedFile.getFileFromOffset()).toInt()

        // 3. 读取消息总大小（前4字节）
        val sizeBuffer = mappedFile.getMessage(fileOffset, 4)
            ?: return null

        val totalSize = sizeBuffer.int

        // 4. 读取完整消息
        val messageBuffer = mappedFile.getMessage(fileOffset, totalSize + 4)
            ?: return null

        // 5. 解码消息
        return MessageCodec.decode(messageBuffer)
    }

    /**
     * 刷盘
     */
    suspend fun flush(flushLeastPages: Int = 0): Boolean {
        val request = FlushRequest(flushLeastPages, CompletableDeferred())
        flushChannel.send(request)
        return request.deferred.await()
    }

    // ==================== 状态查询 ====================

    /**
     * 获取最小偏移量
     */
    fun getMinOffset(): Long {
        return mappedFileQueue.getMinOffset()
    }

    /**
     * 获取最大偏移量
     */
    fun getMaxOffset(): Long {
        return _maxOffset.value
    }

    /**
     * 获取已刷盘偏移量
     */
    fun getFlushedOffset(): Long {
        val lastFile = mappedFileQueue.getLastMappedFile() ?: return 0L
        return lastFile.getFileFromOffset() + lastFile.flushedPosition()
    }

    /**
     * 获取文件数量
     */
    fun getFileCount(): Int {
        return mappedFileQueue.size()
    }
}
