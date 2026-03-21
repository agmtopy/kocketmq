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
import java.nio.ByteBuffer

/**
 * BatchSendMessageProcessor测试
 */
class BatchSendMessageProcessorTest {

    private val testDir = "/tmp/kocketmq/test/batch"
    private lateinit var brokerConfig: BrokerConfig
    private lateinit var brokerController: BrokerController
    private lateinit var batchProcessor: BatchSendMessageProcessor

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

        batchProcessor = BatchSendMessageProcessor(brokerController)
    }

    @AfterEach
    fun tearDown(): Unit = runBlocking {
        brokerController.shutdown()
        File(testDir).deleteRecursively()
    }

    @Test
    fun `测试批量发送消息`() {
        // 构建批量消息
        val batchBody = encodeBatchMessages(listOf(
            "Message 1".toByteArray(),
            "Message 2".toByteArray(),
            "\\1".toByteArray()
        ))

        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_BATCH_MESSAGE, null)
        request.extFields = hashMapOf(
            "topic" to "TestTopic",
            "queueId" to "0"
        )
        request.setBody(batchBody)

        val response = batchProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SUCCESS, response!!.code)
        assertNotNull(response!!.extFields)
        assertEquals("3", response!!.extFields!!["count"])
        assertEquals("3", response!!.extFields!!["successCount"])
    }

    @Test
    fun `测试批量发送到自动创建Topic`() {
        val batchBody = encodeBatchMessages(listOf(
            "Auto message".toByteArray()
        ))

        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_BATCH_MESSAGE, null)
        request.extFields = hashMapOf(
            "topic" to "AutoTopic",
            "queueId" to "0"
        )
        request.setBody(batchBody)

        val response = batchProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SUCCESS, response!!.code)
        assertNotNull(brokerController.topicConfigManager.getTopicConfig("AutoTopic"))
    }

    @Test
    fun `测试空批量消息`() {
        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_BATCH_MESSAGE, null)
        request.extFields = hashMapOf(
            "topic" to "TestTopic",
            "queueId" to "0"
        )
        request.setBody(ByteArray(0))

        val response = batchProcessor.processRequest(null, request)

        assertEquals(RemotingSysResponseCode.SYSTEM_ERROR, response!!.code)
    }

    /**
     * 编码批量消息
     */
    private fun encodeBatchMessages(messages: List<ByteArray>): ByteArray {
        val buffer = ByteBuffer.allocate(1024 * 1024)  // 1MB

        // 写入消息数量
        buffer.putInt(messages.size)

        // 写入每条消息
        for (msg in messages) {
            buffer.putInt(msg.size)
            buffer.put(msg)
        }

        buffer.flip()
        val result = ByteArray(buffer.remaining())
        buffer.get(result)
        return result
    }
}
