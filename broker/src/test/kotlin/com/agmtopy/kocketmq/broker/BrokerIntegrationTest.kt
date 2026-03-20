package com.agmtopy.kocketmq.broker

import com.agmtopy.kocketmq.broker.config.BrokerConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Broker集成测试
 *
 * 测试Broker的完整生命周期和基本功能
 */
class BrokerIntegrationTest {

    private val testDir = "/tmp/kocketmq/test/integration"
    private lateinit var brokerConfig: BrokerConfig
    private lateinit var brokerController: BrokerController

    @BeforeEach
    fun setUp() {
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()

        brokerConfig = BrokerConfig(
            brokerName = "IntegrationTestBroker",
            brokerId = 0,
            clusterName = "TestCluster",
            listenPort = 10911,
            storePathRootDir = testDir,
            autoCreateTopicEnable = true,
            defaultTopicQueueNums = 8
        )
    }

    @AfterEach
    fun tearDown() = runBlocking {
        brokerController.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test broker full lifecycle`() = runBlocking {
        // 1. 创建Broker
        brokerController = BrokerController(brokerConfig)

        // 2. 初始化
        val initialized = brokerController.initialize()
        assertTrue(initialized, "Broker should initialize successfully")

        // 3. 启动
        brokerController.start()

        // 4. 验证状态
        assertTrue(brokerController.messageStore.isRunning())
        assertEquals(10911, brokerController.getListenPort())
        assertEquals("IntegrationTestBroker", brokerController.getBrokerName())

        // 5. 关闭
        brokerController.shutdown()

        // 6. 验证已关闭
        assertFalse(brokerController.messageStore.isRunning())
    }

    @Test
    fun `test broker with message store`() = runBlocking {
        brokerController = BrokerController(brokerConfig)
        assertTrue(brokerController.initialize())
        brokerController.start()

        // 验证存储组件已启动
        assertNotNull(brokerController.messageStore)
        assertTrue(brokerController.messageStore.isRunning())

        // 验证初始状态
        assertEquals(0L, brokerController.messageStore.getMinOffset())
        assertEquals(0L, brokerController.messageStore.getMaxOffset())
        assertEquals(0, brokerController.messageStore.getCommitLogFileCount())
    }

    @Test
    fun `test broker with topic manager`() = runBlocking {
        brokerController = BrokerController(brokerConfig)
        assertTrue(brokerController.initialize())
        brokerController.start()

        // 验证Topic配置管理器已加载
        assertNotNull(brokerController.topicConfigManager)

        // 应该有默认Topic
        assertTrue(brokerController.topicConfigManager.getTopicCount() >= 1)
        assertNotNull(brokerController.topicConfigManager.getTopicConfig("TBW102"))
    }

    @Test
    fun `test broker with offset manager`() = runBlocking {
        brokerController = BrokerController(brokerConfig)
        assertTrue(brokerController.initialize())
        brokerController.start()

        // 验证Offset管理器已初始化
        assertNotNull(brokerController.consumerOffsetManager)
        assertEquals(0, brokerController.consumerOffsetManager.getOffsetTableSize())
    }

    @Test
    fun `test broker restart recovery`() = runBlocking {
        // 第一个生命周期：写入数据
        brokerController = BrokerController(brokerConfig)
        assertTrue(brokerController.initialize())
        brokerController.start()

        // TODO: 写入一些消息
        // brokerController.messageStore.putMessage(...)

        // 刷盘并关闭
        brokerController.messageStore.flush()
        brokerController.shutdown()

        // 第二个生命周期：重启并恢复
        val brokerController2 = BrokerController(brokerConfig)
        assertTrue(brokerController2.initialize())
        brokerController2.start()

        // 验证数据恢复
        // TODO: 验证消息能被读取

        brokerController2.shutdown()
    }

    @Test
    fun `test broker configuration persistence`() = runBlocking {
        // 第一个生命周期
        brokerController = BrokerController(brokerConfig)
        assertTrue(brokerController.initialize())
        brokerController.start()

        // 创建Topic
        brokerController.topicConfigManager.updateTopicConfig(
            com.agmtopy.kocketmq.common.TopicConfig(
                topicName = "PersistentTopic",
                readQueueNums = 4,
                writeQueueNums = 4,
                perm = com.agmtopy.kocketmq.common.constant.PermName.PERM_READ or
                        com.agmtopy.kocketmq.common.constant.PermName.PERM_WRITE
            )
        )

        // 提交offset
        brokerController.consumerOffsetManager.commitOffset(
            "TestTopic", "TestGroup", 0, 100L
        )

        // 持久化并关闭
        brokerController.topicConfigManager.persist()
        brokerController.consumerOffsetManager.persist()
        brokerController.shutdown()

        // 第二个生命周期：重启
        val brokerController2 = BrokerController(brokerConfig)
        assertTrue(brokerController2.initialize())
        brokerController2.start()

        // TODO: 验证配置恢复
        // val topicConfig = brokerController2.topicConfigManager.getTopicConfig("PersistentTopic")
        // assertNotNull(topicConfig)

        // val offset = brokerController2.consumerOffsetManager.queryOffset("TestTopic", "TestGroup", 0)
        // assertEquals(100L, offset)

        brokerController2.shutdown()
    }

    @Test
    fun `test broker network server`() = runBlocking {
        brokerController = BrokerController(brokerConfig)
        assertTrue(brokerController.initialize())
        brokerController.start()

        // 验证网络服务器已启动
        assertNotNull(brokerController.remotingServer)
        assertEquals(10911, brokerController.remotingServer.localListenPort())

        // TODO: 测试网络连接
        // 可以使用Netty客户端连接测试
    }
}
