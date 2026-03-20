package com.agmtopy.kocketmq.broker.store

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 消息存储结果
 */
data class PutMessageResult(
    val status: PutMessageStatus,
    val appendMessageResult: AppendMessageResult? = null
)

/**
 * 消息存储状态
 */
enum class PutMessageStatus {
    PUT_OK,                      // 成功
    PUT_COMMITLOG_ERROR,         // CommitLog写入失败
    PUT_CONSUMEQUEUE_ERROR       // ConsumeQueue写入失败
}

/**
 * 消息查询结果
 */
data class GetMessageResult(
    val status: GetMessageStatus,
    val message: MessageExt? = null
)

/**
 * 消息查询状态
 */
enum class GetMessageStatus {
    GET_OK,                      // 成功
    GET_NOT_FOUND,               // 消息不存在
    GET_COMMITLOG_ERROR          // CommitLog读取失败
}

/**
 * MessageStore Actor
 *
 * 消息存储引擎的核心组件，整合CommitLog和ConsumeQueue。
 * 提供消息的写入、查询和刷盘功能。
 */
class MessageStoreActor(
    private val storePath: String,
    private val commitLogFileSize: Int = 1024 * 1024 * 1024,  // 默认1GB
    private val consumeQueueFileSize: Int = 1024 * 1024 * 6   // 默认6MB
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(MessageStoreActor::class.java)
    }

    // ==================== 核心组件 ====================

    // CommitLog Actor
    private val commitLog = CommitLogActor(storePath, commitLogFileSize)

    // ConsumeQueue Builder Actor
    private val consumeQueueBuilder = ConsumeQueueBuilderActor(storePath, consumeQueueFileSize)

    // ==================== 状态管理 ====================

    // 运行状态
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    // ==================== 生命周期管理 ====================

    /**
     * 加载存储引擎
     */
    suspend fun load(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                log.info("Loading MessageStore...")

                // 1. 加载CommitLog
                val commitLogLoaded = commitLog.load()
                if (!commitLogLoaded) {
                    log.error("Load CommitLog failed")
                    return@withContext false
                }

                // 2. 加载ConsumeQueue（可选，延迟加载）
                // ConsumeQueue会在第一次构建时自动创建

                log.info("MessageStore loaded successfully")
                true
            } catch (e: Exception) {
                log.error("Load MessageStore exception", e)
                false
            }
        }
    }

    /**
     * 启动存储引擎
     */
    fun start() {
        log.info("Starting MessageStore...")

        // 启动CommitLog
        commitLog.start()

        // 启动ConsumeQueueBuilder
        consumeQueueBuilder.start()

        _running.value = true

        log.info("MessageStore started")
    }

    /**
     * 关闭存储引擎
     */
    suspend fun shutdown() {
        log.info("Shutting down MessageStore...")

        _running.value = false

        // 关闭ConsumeQueueBuilder
        consumeQueueBuilder.shutdown()

        // 关闭CommitLog
        commitLog.shutdown()

        log.info("MessageStore shutdown complete")
    }

    // ==================== 消息操作 ====================

    /**
     * 存储消息
     *
     * @param message 消息对象
     * @return 存储结果
     */
    suspend fun putMessage(message: MessageExt): PutMessageResult {
        // 1. 写入CommitLog
        val appendResult = commitLog.appendMessage(message)

        if (appendResult.status != AppendMessageStatus.PUT_OK) {
            log.error("Put message to CommitLog failed: {}", appendResult.status)
            return PutMessageResult(PutMessageStatus.PUT_COMMITLOG_ERROR)
        }

        // 2. 构建ConsumeQueue索引
        try {
            consumeQueueBuilder.buildConsumeQueue(
                topic = message.topic,
                queueId = message.queueId,
                phyOffset = appendResult.wroteOffset,
                size = appendResult.wroteBytes,
                tagsCode = 0  // TODO: 从消息属性中提取tagsCode
            )
        } catch (e: Exception) {
            log.error("Build ConsumeQueue failed", e)
            return PutMessageResult(PutMessageStatus.PUT_CONSUMEQUEUE_ERROR, appendResult)
        }

        return PutMessageResult(PutMessageStatus.PUT_OK, appendResult)
    }

    /**
     * 查询消息
     *
     * @param topic 主题
     * @param queueId 队列ID
     * @param logicOffset 逻辑偏移量
     * @return 查询结果
     */
    suspend fun getMessage(
        topic: String,
        queueId: Int,
        logicOffset: Long
    ): GetMessageResult {
        // 1. 从ConsumeQueue获取物理偏移量
        val physicOffsets = consumeQueueBuilder.getPhysicOffsets(
            topic, queueId, logicOffset, 1
        )

        if (physicOffsets.isEmpty()) {
            return GetMessageResult(GetMessageStatus.GET_NOT_FOUND)
        }

        val phyOffset = physicOffsets[0]

        // 2. 从CommitLog读取消息
        val message = commitLog.getMessage(phyOffset)

        if (message == null) {
            log.error("Get message from CommitLog failed: offset={}", phyOffset)
            return GetMessageResult(GetMessageStatus.GET_COMMITLOG_ERROR)
        }

        return GetMessageResult(GetMessageStatus.GET_OK, message)
    }

    /**
     * 批量查询消息
     *
     * @param topic 主题
     * @param queueId 队列ID
     * @param startLogicOffset 起始逻辑偏移量
     * @param maxNums 最大数量
     * @return 消息列表
     */
    suspend fun getMessages(
        topic: String,
        queueId: Int,
        startLogicOffset: Long,
        maxNums: Int
    ): List<MessageExt> {
        // 1. 批量获取物理偏移量
        val physicOffsets = consumeQueueBuilder.getPhysicOffsets(
            topic, queueId, startLogicOffset, maxNums
        )

        if (physicOffsets.isEmpty()) {
            return emptyList()
        }

        // 2. 批量读取消息
        val messages = mutableListOf<MessageExt>()
        for (phyOffset in physicOffsets) {
            val message = commitLog.getMessage(phyOffset)
            if (message != null) {
                messages.add(message)
            }
        }

        return messages
    }

    // ==================== 状态查询 ====================

    /**
     * 获取CommitLog最小偏移量
     */
    fun getMinOffset(): Long {
        return commitLog.getMinOffset()
    }

    /**
     * 获取CommitLog最大偏移量
     */
    fun getMaxOffset(): Long {
        return commitLog.getMaxOffset()
    }

    /**
     * 获取已刷盘偏移量
     */
    fun getFlushedOffset(): Long {
        return commitLog.getFlushedOffset()
    }

    /**
     * 获取CommitLog文件数量
     */
    fun getCommitLogFileCount(): Int {
        return commitLog.getFileCount()
    }

    /**
     * 获取ConsumeQueue数量
     */
    fun getConsumeQueueCount(): Int {
        return consumeQueueBuilder.getQueueCount()
    }

    /**
     * 刷盘
     */
    suspend fun flush() {
        // 刷盘CommitLog
        commitLog.flush(0)

        // 刷盘ConsumeQueue
        consumeQueueBuilder.flush()
    }

    /**
     * 获取运行状态
     */
    fun isRunning(): Boolean {
        return _running.value
    }
}
