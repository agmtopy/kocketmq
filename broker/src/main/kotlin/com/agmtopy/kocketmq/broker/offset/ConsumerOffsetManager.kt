package com.agmtopy.kocketmq.broker.offset

import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 消费者偏移量管理器
 *
 * 负责管理消费者组的消费进度：
 * - 记录每个topic-queue的消费offset
 * - 持久化offset到磁盘
 * - 支持offset查询和更新
 */
class ConsumerOffsetManager(
    private val brokerConfig: BrokerConfig
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(ConsumerOffsetManager::class.java)

        const val OFFSET_TABLE_SEPARATOR = "@"
    }

    // ==================== 数据结构 ====================

    /**
     * Offset表
     * key: topic@consumerGroup
     * value: Map<queueId, offset>
     */
    private val offsetTable = ConcurrentHashMap<String, ConcurrentHashMap<Int, Long>>()

    /**
     * 数据版本（用于同步）
     */
    private var dataVersion = DataVersion()

    /**
     * 读写锁
     */
    private val lock = ReentrantReadWriteLock()

    // ==================== 加载与持久化 ====================

    /**
     * 加载offset配置
     */
    suspend fun load(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val configFile = File(brokerConfig.consumerOffsetPath)

                if (!configFile.exists()) {
                    log.info("Consumer offset file not found, start with empty offset")
                    return@withContext true
                }

                log.info("Loading consumer offset from: ${configFile.absolutePath}")

                // TODO: 实现JSON解析加载
                // val content = configFile.readText()
                // val wrapper = Json.decodeFromString<ConsumerOffsetSerializeWrapper>(content)
                // offsetTable.putAll(wrapper.offsetTable)
                // dataVersion = wrapper.dataVersion

                log.info("Loaded ${offsetTable.size} consumer offsets")
                true
            } catch (e: Exception) {
                log.error("Load consumer offset failed", e)
                false
            }
        }
    }

    /**
     * 持久化offset到磁盘
     */
    suspend fun persist() {
        withContext(Dispatchers.IO) {
            try {
                val configFile = File(brokerConfig.consumerOffsetPath)
                configFile.parentFile.mkdirs()

                // TODO: 实现JSON序列化保存
                // val wrapper = ConsumerOffsetSerializeWrapper(
                //     offsetTable = offsetTable.toMap(),
                //     dataVersion = dataVersion
                // )
                // val json = Json.encodeToString(wrapper)
                //
                // // 原子写入
                // val tempFile = File("${configFile.absolutePath}.tmp")
                // tempFile.writeText(json)
                // tempFile.renameTo(configFile)

                log.info("Persisted consumer offset to ${configFile.absolutePath}")
            } catch (e: Exception) {
                log.error("Persist consumer offset failed", e)
            }
        }
    }

    // ==================== Offset操作 ====================

    /**
     * 提交offset
     *
     * @param topic Topic名称
     * @param group 消费者组
     * @param queueId 队列ID
     * @param offset 新offset
     */
    fun commitOffset(topic: String, group: String, queueId: Int, offset: Long) {
        val key = buildOffsetKey(topic, group)

        lock.write {
            val queueOffsetMap = offsetTable.computeIfAbsent(key) {
                ConcurrentHashMap()
            }
            queueOffsetMap[queueId] = offset
            dataVersion.nextVersion()

            log.debug("Commit offset: topic={}, group={}, queueId={}, offset={}",
                topic, group, queueId, offset)
        }
    }

    /**
     * 查询offset
     *
     * @param topic Topic名称
     * @param group 消费者组
     * @param queueId 队列ID
     * @return offset，不存在返回-1
     */
    fun queryOffset(topic: String, group: String, queueId: Int): Long {
        val key = buildOffsetKey(topic, group)

        return lock.read {
            offsetTable[key]?.get(queueId) ?: -1L
        }
    }

    /**
     * 查询Topic下所有队列的offset
     *
     * @param topic Topic名称
     * @param group 消费者组
     * @return Map<queueId, offset>
     */
    fun queryAllOffset(topic: String, group: String): Map<Int, Long> {
        val key = buildOffsetKey(topic, group)

        return lock.read {
            offsetTable[key]?.toMap() ?: emptyMap()
        }
    }

    /**
     * 删除offset
     *
     * @param topic Topic名称
     * @param group 消费者组
     * @param queueId 队列ID（可选，不指定则删除整个Topic的offset）
     */
    fun removeOffset(topic: String, group: String, queueId: Int? = null) {
        val key = buildOffsetKey(topic, group)

        lock.write {
            if (queueId != null) {
                offsetTable[key]?.remove(queueId)
            } else {
                offsetTable.remove(key)
            }
            dataVersion.nextVersion()

            log.info("Removed offset: topic={}, group={}, queueId={}",
                topic, group, queueId ?: "all")
        }
    }

    /**
     * 获取offset表大小
     */
    fun getOffsetTableSize(): Int {
        return offsetTable.size
    }

    /**
     * 获取所有offset（用于管理查询）
     */
    fun getAllOffsetTable(): Map<String, Map<Int, Long>> {
        return lock.read {
            offsetTable.mapValues { it.value.toMap() }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建offset key
     */
    private fun buildOffsetKey(topic: String, group: String): String {
        return "$topic$OFFSET_TABLE_SEPARATOR$group"
    }

    /**
     * 数据版本
     */
    data class DataVersion(
        var stateVersion: Long = 0,
        var timestamp: Long = System.currentTimeMillis()
    ) {
        fun nextVersion() {
            stateVersion++
            timestamp = System.currentTimeMillis()
        }
    }
}
