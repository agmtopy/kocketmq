package com.agmtopy.kocketmq.broker

import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.broker.offset.ConsumerOffsetManager
import com.agmtopy.kocketmq.broker.store.MessageStoreActor
import com.agmtopy.kocketmq.broker.topic.TopicConfigManager
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.netty.NettyRemotingServer
import com.agmtopy.kocketmq.remoting.netty.NettyServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Broker主控制器
 *
 * 负责管理Broker的所有组件生命周期，包括：
 * - MessageStore：消息存储
 * - RemotingServer：网络服务
 * - 配置管理器
 */
class BrokerController(
    private val brokerConfig: BrokerConfig
) {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(BrokerController::class.java)
    }

    // ==================== 核心组件 ====================

    /**
     * 消息存储引擎
     */
    val messageStore: MessageStoreActor = MessageStoreActor(
        storePath = brokerConfig.storePathRootDir,
        commitLogFileSize = brokerConfig.commitLogFileSize,
        consumeQueueFileSize = brokerConfig.mappedFileSizeConsumeQueue
    )

    /**
     * Netty远程服务器
     */
    val remotingServer: NettyRemotingServer

    /**
     * Topic配置管理器
     */
    val topicConfigManager: TopicConfigManager = TopicConfigManager(brokerConfig)

    /**
     * 消费者offset管理器
     */
    val consumerOffsetManager: ConsumerOffsetManager = ConsumerOffsetManager(brokerConfig)

    // ==================== 状态管理 ====================

    private var initialized = false

    init {
        // 初始化Netty服务器配置
        val nettyServerConfig = NettyServerConfig()
        nettyServerConfig.setListenPort(brokerConfig.listenPort)

        remotingServer = NettyRemotingServer(nettyServerConfig)
    }

    // ==================== 生命周期管理 ====================

    /**
     * 初始化Broker
     */
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                log.info("========================================")
                log.info("Initializing Broker: ${brokerConfig.brokerName}")
                log.info("Cluster: ${brokerConfig.clusterName}")
                log.info("Listen Port: ${brokerConfig.listenPort}")
                log.info("========================================")

                // 1. 加载消息存储
                log.info("Loading message store...")
                val storeLoaded = messageStore.load()
                if (!storeLoaded) {
                    log.error("Load message store failed")
                    return@withContext false
                }
                log.info("Message store loaded successfully")

                // 2. 加载Topic配置
                log.info("Loading topic config...")
                val topicConfigLoaded = topicConfigManager.load()
                if (!topicConfigLoaded) {
                    log.warn("Load topic config failed, will use default")
                }

                // 3. 加载消费者offset
                log.info("Loading consumer offset...")
                val consumerOffsetLoaded = consumerOffsetManager.load()
                if (!consumerOffsetLoaded) {
                    log.warn("Load consumer offset failed, will start fresh")
                }

                initialized = true
                log.info("Broker initialized successfully")
                true
            } catch (e: Exception) {
                log.error("Initialize Broker exception", e)
                false
            }
        }
    }

    /**
     * 启动Broker
     */
    fun start() {
        if (!initialized) {
            throw IllegalStateException("Broker not initialized, please call initialize() first")
        }

        log.info("Starting Broker...")

        // 1. 启动消息存储
        log.info("Starting message store...")
        messageStore.start()

        // 2. 启动网络服务器
        log.info("Starting remoting server...")
        remotingServer.start()

        log.info("========================================")
        log.info("Broker Started Successfully")
        log.info("Broker Name: ${brokerConfig.brokerName}")
        log.info("Listen Port: ${remotingServer.localListenPort()}")
        log.info("========================================")
    }

    /**
     * 关闭Broker
     */
    suspend fun shutdown() {
        log.info("Shutting down Broker...")

        // 1. 关闭网络服务器
        log.info("Shutting down remoting server...")
        remotingServer.shutdown()

        // 2. 关闭消息存储
        log.info("Shutting down message store...")
        messageStore.shutdown()

        // 3. 持久化配置
        log.info("Persisting topic config...")
        topicConfigManager.persist()

        log.info("Persisting consumer offset...")
        consumerOffsetManager.persist()

        log.info("Broker shutdown complete")
    }

    // ==================== 状态查询 ====================

    /**
     * 获取Broker名称
     */
    fun getBrokerName(): String = brokerConfig.brokerName

    /**
     * 获取Broker ID
     */
    fun getBrokerId(): Long = brokerConfig.brokerId

    /**
     * 获取集群名称
     */
    fun getClusterName(): String = brokerConfig.clusterName

    /**
     * 获取监听端口
     */
    fun getListenPort(): Int = remotingServer.localListenPort()
}
