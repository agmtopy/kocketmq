package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.broker.store.MessageExt
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
 * PullMessageProcessor测试
 */
class PullMessageProcessorTest {

    private val testDir = "/tmp/kocketmq/test/pullmessage"
    private lateinit var brokerConfig: BrokerConfig
    private lateinit var brokerController: BrokerController
    private lateinit var pullMessageProcessor: PullMessageProcessor
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

        pullMessageProcessor = PullMessageProcessor(brokerController)
        sendMessageProcessor = SendMessageProcessor(brokerController)
    }

    @AfterEach
    fun tearDown() = runBlocking {
        brokerController.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `test pull message success`() = runBlocking {
        // 先发送消息
        val sendRequest = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, null)
        sendRequest.extFields = mapOf(
            "topic" to "TestTopic",
            "queueId" to "0"
        )
        sendRequest.body = "Test message".toByteArray()
        sendMessageProcessor.processRequest(null, sendRequest)

        // 拉取消息
        val pullRequest = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, null)
        pullRequest.extFields = mapOf(
            "consumerGroup" to "TestGroup",
            "topic" to "TestTopic",
            "queueId" to "0",
            "queueOffset" to "0",
            "maxMsgNums" to "10"
        )

        val response = pullMessageProcessor.processRequest(null, pullRequest)

        // 验证响应
        assertEquals(ResponseCode.SUCCESS, response.code)
        assertNotNull(response.extFields)
        assertTrue(response.extFields!!.containsKey("nextBeginOffset"))
        assertTrue(response.extFields!!.containsKey("minOffset"))
        assertTrue(response.extFields!!.containsKey("maxOffset"))
    }

    @Test
    fun `test pull message from non-existent topic`() {
        val pullRequest = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, null)
        pullRequest.extFields = mapOf(
            "consumerGroup" to "TestGroup",
            "topic" to "NonExistentTopic",
            "queueId" to "0",
            "queueOffset" to "0"
        )

        val response = pullMessageProcessor.processRequest(null, pullRequest)

        // Topic不存在应该返回错误
        assertEquals(ResponseCode.TOPIC_NOT_EXIST, response.code)
    }

    @Test
    fun `test pull message with invalid offset`() = runBlocking {
        // 先发送消息
        val sendRequest = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, null)
        sendRequest.extFields = mapOf(
            "topic" to "TestTopic",
            "queueId" to "0"
        )
        sendRequest.body = "Test message".toByteArray()
        sendMessageProcessor.processRequest(null, sendRequest)

        // 拉取超大offset
        val pullRequest = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, null)
        pullRequest.extFields = mapOf(
            "consumerGroup" to "TestGroup",
            "topic" to "TestTopic",
            "queueId" to "0",
            "queueOffset" to "999999",
            "maxMsgNums" to "10"
        )

        val response = pullMessageProcessor.processRequest(null, pullRequest)

        // 应该返回成功但消息为空
        assertEquals(ResponseCode.SUCCESS, response.code)
    }

    @Test
    fun `test pull message with max nums`() = runBlocking {
        // 发送多条消息
        for (i in 1..5) {
            val sendRequest = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, null)
            sendRequest.extFields = mapOf(
                "topic" to "TestTopic",
                "queueId" to "0"
            )
            sendRequest.body = "Message $i".toByteArray()
            sendMessageProcessor.processRequest(null, sendRequest)
        }

        // 拉取3条消息
        val pullRequest = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, null)
        pullRequest.extFields = mapOf(
            "consumerGroup" to "TestGroup",
            "topic" to "TestTopic",
            "queueId" to "0",
            "queueOffset" to "0",
            "maxMsgNums" to "3"
        )

        val response = pullMessageProcessor.processRequest(null, pullRequest)

        assertEquals(ResponseCode.SUCCESS, response.code)
        // nextBeginOffset应该是3
        assertEquals(3L, response.extFields!!["nextBeginOffset"]?.toLong())
    }

    @Test
    fun `test pull message without required fields`() {
        val pullRequest = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, null)
        pullRequest.extFields = mapOf(
            "topic" to "TestTopic"
            // 缺少consumerGroup, queueId等
        )

        val response = pullMessageProcessor.processRequest(null, pullRequest)

        assertEquals(ResponseCode.SYSTEM_ERROR, response.code)
    }
}
