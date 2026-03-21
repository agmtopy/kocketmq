package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.protocol.RemotingSysResponseCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * AdminBrokerProcessor测试
 */
class AdminBrokerProcessorTest {

    private val testDir = "/tmp/kocketmq/test/admin"
    private lateinit var brokerConfig: BrokerConfig
    private lateinit var brokerController: BrokerController
    private lateinit var adminBrokerProcessor: AdminBrokerProcessor

    @BeforeEach
    fun setUp() = runBlocking {
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()

        brokerConfig = BrokerConfig(
            brokerName = "TestBroker",
            listenPort = 10911,
            storePathRootDir = testDir
        )

        brokerController = BrokerController(brokerConfig)
        brokerController.initialize()
        brokerController.start()

        adminBrokerProcessor = AdminBrokerProcessor(brokerController)
    }

    @AfterEach
    fun tearDown(): Unit = runBlocking {
        brokerController.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `测试创建Topic`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.UPDATE_AND_CREATE_TOPIC, null)
        request.extFields = hashMapOf(
            "topicName" to "TestTopic",
            "readQueueNums" to "4",
            "writeQueueNums" to "4",
            "perm" to "6"
        )

        val response = adminBrokerProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SUCCESS, response!!.code)
        assertNotNull(brokerController.topicConfigManager.getTopicConfig("TestTopic"))
    }

    @Test
    fun `测试获取所有Topic配置`() {
        // 创建Topic
        brokerController.topicConfigManager.updateTopicConfig(
            com.agmtopy.kocketmq.common.TopicConfig("Topic1", 4, 4, 6)
        )

        val request = RemotingCommand.createRequestCommand(RequestCode.GET_ALL_TOPIC_CONFIG, null)
        val response = adminBrokerProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SUCCESS, response!!.code)
        assertNotNull(response!!.getBody())
    }

    @Test
    fun `测试获取Broker配置`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.GET_BROKER_CONFIG, null)
        val response = adminBrokerProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SUCCESS, response!!.code)
        assertNotNull(response!!.getBody())
        val configStr = String(response!!.getBody()!!)
        assertTrue(configStr.contains("TestBroker"))
    }

    @Test
    fun `测试获取Broker运行时信息`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.GET_BROKER_RUNTIME_INFO, null)
        val response = adminBrokerProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SUCCESS, response!!.code)
        assertNotNull(response!!.getBody())
        val infoStr = String(response!!.getBody()!!)
        assertTrue(infoStr.contains("brokerName"))
    }

    @Test
    fun `测试获取最小Offset`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.GET_MIN_OFFSET, null)
        request.extFields = hashMapOf("topic" to "TestTopic")

        val response = adminBrokerProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SUCCESS, response!!.code)
        assertNotNull(response!!.extFields)
        assertTrue(response!!.extFields!!.containsKey("offset"))
    }

    @Test
    fun `测试获取最大Offset`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.GET_MAX_OFFSET, null)
        request.extFields = hashMapOf("topic" to "TestTopic")

        val response = adminBrokerProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SUCCESS, response!!.code)
        assertNotNull(response!!.extFields)
        assertTrue(response!!.extFields!!.containsKey("offset"))
    }
}
