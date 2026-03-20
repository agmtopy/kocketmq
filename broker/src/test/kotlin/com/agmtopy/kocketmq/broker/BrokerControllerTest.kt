package com.agmtopy.kocketmq.broker

import com.agmtopy.kocketmq.broker.config.BrokerConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * BrokerController测试
 */
class BrokerControllerTest {

    private val testDir = "/tmp/kocketmq/test/broker"
    private lateinit var brokerConfig: BrokerConfig
    private lateinit var brokerController: BrokerController

    @BeforeEach
    fun setUp() {
        // 清理测试目录
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()

        brokerConfig = BrokerConfig(
            brokerName = "TestBroker",
            brokerId = 0,
            clusterName = "TestCluster",
            listenPort = 10911,
            storePathRootDir = testDir
        )
    }

    @AfterEach
    fun tearDown() = runBlocking {
        brokerController.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test initialize and start`() = runBlocking {
        brokerController = BrokerController(brokerConfig)

        // 初始化
        val initialized = brokerController.initialize()
        assertTrue(initialized)

        // 启动
        brokerController.start()

        // 验证状态
        assertEquals("TestBroker", brokerController.getBrokerName())
        assertEquals(0L, brokerController.getBrokerId())
        assertEquals("TestCluster", brokerController.getClusterName())
        assertEquals(10911, brokerController.getListenPort())
    }

    @Test
    fun `test message store integration`() = runBlocking {
        brokerController = BrokerController(brokerConfig)
        assertTrue(brokerController.initialize())
        brokerController.start()

        // 验证消息存储已启动
        assertTrue(brokerController.messageStore.isRunning())
    }

    @Test
    fun `test broker config`() {
        brokerController = BrokerController(brokerConfig)

        assertEquals("TestBroker", brokerController.getBrokerName())
        assertEquals(0L, brokerController.getBrokerId())
        assertEquals("TestCluster", brokerController.getClusterName())
    }

    @Test
    fun `test shutdown`() = runBlocking {
        brokerController = BrokerController(brokerConfig)
        assertTrue(brokerController.initialize())
        brokerController.start()

        // 关闭应该成功
        brokerController.shutdown()

        // 验证存储已关闭
        assertFalse(brokerController.messageStore.isRunning())
    }
}
