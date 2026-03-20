package com.agmtopy.kocketmq.broker.topic

import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.common.TopicConfig
import com.agmtopy.kocketmq.common.constant.PermName
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
 * Topic配置管理器
 *
 * 负责管理所有Topic的配置信息，包括：
 * - Topic的队列数量
 * - Topic的权限（读写）
 * - 支持动态创建Topic
 * - 配置持久化到磁盘
 */
class TopicConfigManager(
    private val brokerConfig: BrokerConfig
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(TopicConfigManager::class.java)

        const val DEFAULT_TOPIC = "TBW102"  // 默认Topic，用于自动创建
        const val AUTO_CREATE_TOPIC_KEY_TOPIC = "AUTO_CREATE_TOPIC_KEY_TOPIC"
    }

    // ==================== 数据结构 ====================

    /**
     * Topic配置表
     * key: topic name
     * value: TopicConfig
     */
    private val topicConfigTable = ConcurrentHashMap<String, TopicConfig>()

    /**
     * 数据版本（用于同步）
     */
    private var dataVersion = DataVersion()

    /**
     * 读写锁（保护配置表）
     */
    private val lock = ReentrantReadWriteLock()

    // ==================== 初始化与加载 ====================

    /**
     * 加载Topic配置
     */
    suspend fun load(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val configFile = File(brokerConfig.topicConfigPath)

                if (!configFile.exists()) {
                    log.info("Topic config file not found, create default topics")
                    createDefaultTopics()
                    return@withContext true
                }

                log.info("Loading topic config from: ${configFile.absolutePath}")

                // TODO: 实现JSON解析加载
                // val content = configFile.readText()
                // val wrapper = Json.decodeFromString<TopicConfigSerializeWrapper>(content)
                // topicConfigTable.putAll(wrapper.topicConfigTable)
                // dataVersion = wrapper.dataVersion

                log.info("Loaded ${topicConfigTable.size} topics")
                true
            } catch (e: Exception) {
                log.error("Load topic config failed", e)
                false
            }
        }
    }

    /**
     * 创建默认Topic
     */
    private fun createDefaultTopics() {
        // 创建默认Topic（用于自动创建）
        val defaultTopicConfig = TopicConfig(
            topicName = DEFAULT_TOPIC,
            readQueueNums = brokerConfig.defaultTopicQueueNums,
            writeQueueNums = brokerConfig.defaultTopicQueueNums,
            perm = PermName.PERM_READ or PermName.PERM_WRITE
        )

        topicConfigTable[DEFAULT_TOPIC] = defaultTopicConfig

        log.info("Created default topic: $DEFAULT_TOPIC")
    }

    // ==================== Topic操作 ====================

    /**
     * 创建或更新Topic配置
     */
    fun updateTopicConfig(topicConfig: TopicConfig) {
        lock.write {
            topicConfigTable[topicConfig.topicName] = topicConfig
            dataVersion.nextVersion()

            log.info("Updated topic config: ${topicConfig.topicName}")
        }
    }

    /**
     * 获取Topic配置
     */
    fun getTopicConfig(topic: String): TopicConfig? {
        return lock.read {
            topicConfigTable[topic]
        }
    }

    /**
     * 获取或创建Topic配置
     *
     * 如果Topic不存在且允许自动创建，则使用默认配置创建
     */
    fun getTopicConfigOrCreate(topic: String): TopicConfig? {
        // 先尝试读取
        lock.read {
            topicConfigTable[topic]?.let { return it }
        }

        // 不存在，检查是否允许自动创建
        if (!brokerConfig.autoCreateTopicEnable) {
            log.warn("Topic $topic not exist and auto create disabled")
            return null
        }

        // 创建新Topic
        return lock.write {
            // 再次检查（double-check）
            topicConfigTable[topic]?.let { return@write it }

            val newTopicConfig = TopicConfig(
                topicName = topic,
                readQueueNums = brokerConfig.defaultTopicQueueNums,
                writeQueueNums = brokerConfig.defaultTopicQueueNums,
                perm = PermName.PERM_READ or PermName.PERM_WRITE
            )

            topicConfigTable[topic] = newTopicConfig
            dataVersion.nextVersion()

            log.info("Auto created topic: $topic with ${brokerConfig.defaultTopicQueueNums} queues")

            newTopicConfig
        }
    }

    /**
     * 删除Topic配置
     */
    fun deleteTopicConfig(topic: String) {
        lock.write {
            topicConfigTable.remove(topic)
            dataVersion.nextVersion()

            log.info("Deleted topic config: $topic")
        }
    }

    /**
     * 获取所有Topic配置
     */
    fun getAllTopicConfig(): Map<String, TopicConfig> {
        return lock.read {
            topicConfigTable.toMap()
        }
    }

    /**
     * 获取Topic数量
     */
    fun getTopicCount(): Int {
        return topicConfigTable.size
    }

    // ==================== 持久化 ====================

    /**
     * 持久化配置到磁盘
     */
    suspend fun persist() {
        withContext(Dispatchers.IO) {
            try {
                val configFile = File(brokerConfig.topicConfigPath)
                configFile.parentFile.mkdirs()

                // TODO: 实现JSON序列化保存
                // val wrapper = TopicConfigSerializeWrapper(
                //     topicConfigTable = topicConfigTable.toMap(),
                //     dataVersion = dataVersion
                // )
                // val json = Json.encodeToString(wrapper)
                //
                // // 原子写入：先写临时文件，再重命名
                // val tempFile = File("${configFile.absolutePath}.tmp")
                // tempFile.writeText(json)
                // tempFile.renameTo(configFile)

                log.info("Persisted topic config to ${configFile.absolutePath}")
            } catch (e: Exception) {
                log.error("Persist topic config failed", e)
            }
        }
    }

    /**
     * 数据版本（简化实现）
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
