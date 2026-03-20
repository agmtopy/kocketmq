package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.protocol.ResponseCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * SendMessageProcessor测试
 */
class SendMessageProcessorTest {

    private val testDir = "/tmp/kocketmq/test/sendmessage"
    private lateinit var brokerConfig: BrokerConfig
    private lateinit var brokerController: BrokerController
    private lateinit var sendMessageProcessor: SendMessageProcessor

    @BeforeEach
    fun setUp() = runBlocking {
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()

        brokerConfig = BrokerConfig(
            brokerName = "TestBroker",
            listenPort = 10911,
            storePathRootDir = testDir,
            autoCreateTopicEnable = true
        )

        brokerController = BrokerController(brokerConfig)
        brokerController.initialize()
        brokerController.start()

        sendMessageProcessor = SendMessageProcessor(brokerController)
    }

    @AfterEach
    fun tearDown() = runBlocking {
        brokerController.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test send message success`() {
        // 构建请求
        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, null)
        request.extFields = mapOf(
            "topic" to "TestTopic",
            "queueId" to "0",
            "sysFlag" to "0",
            "bornTimestamp" to System.currentTimeMillis().toString(),
            "flag" to "0"
        )
        request.body = "Hello, KocketMQ!".toByteArray()

        // 处理请求
        val response = sendMessageProcessor.processRequest(null, request)

        // 验证响应
        assertEquals(ResponseCode.SUCCESS, response.code)
        assertNotNull(response.extFields)
        assertTrue(response.extFields!!.containsKey("msgId"))
        assertTrue(response.extFields!!.containsKey("queueId"))
        assertTrue(response.extFields!!.containsKey("queueOffset"))
    }

    @Test
    fun `test send message to auto created topic`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, null)
        request.extFields = mapOf(
            "topic" to "AutoCreatedTopic",
            "queueId" to "0"
        )
        request.body = "Test message".toByteArray()

        val response = sendMessageProcessor.processRequest(null, request)

        // 应该自动创建Topic并成功
        assertEquals(ResponseCode.SUCCESS, response.code)
        assertNotNull(brokerController.topicConfigManager.getTopicConfig("AutoCreatedTopic"))
    }

    @Test
    fun `test send message without topic`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, null)
        request.extFields = mapOf(
            "queueId" to "0"
        )
        request.body = "Test message".toByteArray()

        val response = sendMessageProcessor.processRequest(null, request)

        // 应该失败
        assertEquals(ResponseCode.SYSTEM_ERROR, response.code)
    }

    @Test
    fun `test send message v2`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE_V2, null)
        request.extFields = mapOf(
            "topic" to "TestTopicV2",
            "queueId" to "0"
        )
        request.body = "V2 message".toByteArray()

        val response = sendMessageProcessor.processRequest(null, request)

        assertEquals(ResponseCode.SUCCESS, response.code)
    }

    @Test
    fun `test send batch message not supported`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_BATCH_MESSAGE, null)
        request.extFields = mapOf(
            "topic" to "TestTopic",
            "queueId" to "0"
        )

        val response = sendMessageProcessor.processRequest(null, request)

        assertEquals(ResponseCode.REQUEST_CODE_NOT_SUPPORTED, response.code)
    }
}
