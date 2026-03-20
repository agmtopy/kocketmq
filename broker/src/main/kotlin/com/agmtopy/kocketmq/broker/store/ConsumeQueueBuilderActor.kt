package com.agmtopy.kocketmq.broker.store

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File

/**
 * ConsumeQueueBuilder Actor
 *
 * 负责构建和管理所有Topic-Queue的索引。
 * 通过Channel接收构建请求，单协程串行处理。
 */
class ConsumeQueueBuilderActor(
    private val storePath: String,
    private val mappedFileSize: Int = 1024 * 1024 * 6  // 默认6MB
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(ConsumeQueueBuilderActor::class.java)
    }

    // ==================== 内部数据结构 ====================

    /**
     * 构建请求
     */
    private data class BuildRequest(
        val topic: String,
        val queueId: Int,
        val phyOffset: Long,
        val size: Int,
        val tagsCode: Long,
        val deferred: CompletableDeferred<Unit>
    )

    /**
     * 查询请求
     */
    private data class QueryRequest(
        val topic: String,
        val queueId: Int,
        val logicOffset: Long,
        val maxNums: Int,
        val deferred: CompletableDeferred<List<Long>>
    )

    // ==================== 状态管理 ====================

    // 请求Channel
    private val buildChannel = Channel<BuildRequest>(Channel.UNLIMITED)
    private val queryChannel = Channel<QueryRequest>(Channel.UNLIMITED)

    // ConsumeQueue缓存（Topic-QueueId -> ConsumeQueue）
    private val consumeQueueTable = mutableMapOf<String, ConsumeQueue>()

    // 处理协程
    private var buildJob: Job? = null
    private var queryJob: Job? = null

    // ==================== 生命周期管理 ====================

    /**
     * 启动Actor
     */
    fun start() {
        // 启动构建协程（单个协程，串行处理）
        buildJob = CoroutineScope(Dispatchers.IO).launch {
            log.info("ConsumeQueueBuilderActor build coroutine started")

            for (request in buildChannel) {
                try {
                    processBuild(request)
                    request.deferred.complete(Unit)
                } catch (e: Exception) {
                    log.error("Process build failed", e)
                    request.deferred.completeExceptionally(e)
                }
            }
        }

        // 启动查询协程
        queryJob = CoroutineScope(Dispatchers.IO).launch {
            log.info("ConsumeQueueBuilderActor query coroutine started")

            for (request in queryChannel) {
                try {
                    val result = processQuery(request)
                    request.deferred.complete(result)
                } catch (e: Exception) {
                    log.error("Process query failed", e)
                    request.deferred.completeExceptionally(e)
                }
            }
        }

        log.info("ConsumeQueueBuilderActor started")
    }

    /**
     * 关闭Actor
     */
    suspend fun shutdown() {
        log.info("ConsumeQueueBuilderActor shutting down...")

        // 取消协程
        buildJob?.cancelAndJoin()
        queryJob?.cancelAndJoin()

        // 销毁所有ConsumeQueue
        for (cq in consumeQueueTable.values) {
            cq.destroy()
        }
        consumeQueueTable.clear()

        log.info("ConsumeQueueBuilderActor shutdown complete")
    }

    // ==================== 索引操作 ====================

    /**
     * 构建ConsumeQueue索引
     */
    suspend fun buildConsumeQueue(
        topic: String,
        queueId: Int,
        phyOffset: Long,
        size: Int,
        tagsCode: Long = 0
    ) {
        val request = BuildRequest(
            topic, queueId, phyOffset, size, tagsCode,
            CompletableDeferred()
        )
        buildChannel.send(request)
        request.deferred.await()
    }

    /**
     * 处理构建请求
     */
    private suspend fun processBuild(request: BuildRequest) {
        // 1. 获取或创建ConsumeQueue
        val key = buildKey(request.topic, request.queueId)
        var cq = consumeQueueTable[key]

        if (cq == null) {
            cq = ConsumeQueue(storePath, request.topic, request.queueId, mappedFileSize)
            cq.load()
            consumeQueueTable[key] = cq
        }

        // 2. 追加索引单元
        cq.append(request.phyOffset, request.size, request.tagsCode)
    }

    /**
     * 获取物理偏移量列表
     */
    suspend fun getPhysicOffsets(
        topic: String,
        queueId: Int,
        logicOffset: Long,
        maxNums: Int
    ): List<Long> {
        val request = QueryRequest(
            topic, queueId, logicOffset, maxNums,
            CompletableDeferred()
        )
        queryChannel.send(request)
        return request.deferred.await()
    }

    /**
     * 处理查询请求
     */
    private fun processQuery(request: QueryRequest): List<Long> {
        val key = buildKey(request.topic, request.queueId)
        val cq = consumeQueueTable[key]
            ?: return emptyList()

        return cq.getPhysicOffsets(request.logicOffset, request.maxNums)
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建key
     */
    private fun buildKey(topic: String, queueId: Int): String {
        return "$topic-$queueId"
    }

    /**
     * 刷盘所有ConsumeQueue
     */
    suspend fun flush() {
        withContext(Dispatchers.IO) {
            for (cq in consumeQueueTable.values) {
                cq.flush()
            }
        }
    }

    /**
     * 获取ConsumeQueue
     */
    fun getConsumeQueue(topic: String, queueId: Int): ConsumeQueue? {
        return consumeQueueTable[buildKey(topic, queueId)]
    }

    /**
     * 获取队列数量
     */
    fun getQueueCount(): Int {
        return consumeQueueTable.size
    }
}
