package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.store.MessageExt
import com.agmtopy.kocketmq.broker.store.PutMessageStatus
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.netty.NettyRequestProcessor
import com.agmtopy.kocketmq.remoting.protocol.RemotingSysResponseCode
import com.agmtopy.kocketmq.remoting.protocol.ResponseCode
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.runBlocking

/**
 * 批量消息处理器
 *
 * 处理批量消息发送请求，性能优化：
 * - 批量写入CommitLog
 * - 减少网络往返
 * - 提高吞吐量
 */
class BatchSendMessageProcessor(
    private val brokerController: BrokerController
) : NettyRequestProcessor {

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(BatchSendMessageProcessor::class.java)
    }

    override fun processRequest(ctx: ChannelHandlerContext?, request: RemotingCommand?): RemotingCommand? {
        if (request == null) {
            return RemotingCommand.createResponseCommand(RemotingSysResponseCode.SYSTEM_ERROR, "Request is null")
        }
        return when (request.code) {
            RequestCode.SEND_BATCH_MESSAGE -> processBatchSendMessage(request)
            else -> RemotingCommand.createResponseCommand(
                RemotingSysResponseCode.REQUEST_CODE_NOT_SUPPORTED,
                "不支持此请求码: ${request.code}"
            )
        }
    }

    override fun rejectRequest(): Boolean = false

    /**
     * 处理批量发送消息
     */
    private fun processBatchSendMessage(request: RemotingCommand): RemotingCommand? {
        return try {
            // 1. 解码请求
            val topic = request.extFields?.get("topic")
            val queueId = request.extFields?.get("queueId")?.toInt() ?: 0

            if (topic.isNullOrBlank()) {
                return RemotingCommand.createResponseCommand(
                    RemotingSysResponseCode.SYSTEM_ERROR,
                    "Topic不能为空"
                )
            }

            // 2. 获取Topic配置
            val topicConfig = brokerController.topicConfigManager.getTopicConfigOrCreate(topic)

            if (topicConfig == null) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.TOPIC_NOT_EXIST,
                    "Topic不存在且自动创建已禁用"
                )
            }

            // 3. 解码批量消息
            val messages = decodeBatchMessages(request.getBody(), topic, queueId)

            if (messages.isEmpty()) {
                return RemotingCommand.createResponseCommand(
                    RemotingSysResponseCode.SYSTEM_ERROR,
                    "批量消息为空"
                )
            }

            // 4. 批量存储消息
            val results = mutableListOf<BatchMessageResult>()

            runBlocking {
                for (message in messages) {
                    val result = brokerController.messageStore.putMessage(message)
                    results.add(
                        BatchMessageResult(
                            status = result.status.name,
                            offset = result.appendMessageResult?.wroteOffset ?: -1
                        )
                    )
                }
            }

            // 5. 统计成功数量
            val successCount = results.count { it.status == PutMessageStatus.PUT_OK.name }

            return if (successCount == results.size) {
                // 全部成功
                log.info("批量发送成功: topic=$topic, count=$successCount")

                val response = RemotingCommand.createResponseCommand(RemotingSysResponseCode.SUCCESS, "OK")
                response?.setExtFields(
                    mapOf(
                        "count" to results.size.toString(),
                        "successCount" to successCount.toString()
                    )
                )
                response

            } else {
                // 部分失败
                log.warn("批量发送部分失败: topic=$topic, total=${results.size}, success=$successCount")

                val response = RemotingCommand.createResponseCommand(
                    RemotingSysResponseCode.SYSTEM_ERROR,
                    "部分消息发送失败"
                )
                response?.setExtFields(
                    mapOf(
                        "count" to results.size.toString(),
                        "successCount" to successCount.toString()
                    )
                )
                response
            }

        } catch (e: Exception) {
            log.error("批量发送消息失败", e)
            RemotingCommand.createResponseCommand(
                RemotingSysResponseCode.SYSTEM_ERROR,
                "批量发送失败: ${e.message}"
            )
        }
    }

    /**
     * 解码批量消息
     *
     * 消息格式：[count(4)][msg1Size(4)][msg1Body][msg2Size(4)][msg2Body]...
     */
    private fun decodeBatchMessages(body: ByteArray?, topic: String, queueId: Int): List<MessageExt> {
        if (body == null || body.isEmpty()) {
            return emptyList()
        }

        val messages = mutableListOf<MessageExt>()
        val buffer = java.nio.ByteBuffer.wrap(body)

        try {
            // 读取消息数量
            val count = buffer.int

            for (i in 0 until count) {
                // 读取消息大小
                val msgSize = buffer.int

                // 读取消息体
                val msgBody = ByteArray(msgSize)
                buffer.get(msgBody)

                // 构建消息对象
                val message = MessageExt(
                    topic = topic,
                    queueId = queueId,
                    body = msgBody,
                    bodyCRC = MessageExt.calculateCRC32(msgBody),
                    storeTimestamp = System.currentTimeMillis()
                )

                messages.add(message)
            }

        } catch (e: Exception) {
            log.error("解码批量消息失败", e)
        }

        return messages
    }

    /**
     * 批量消息结果
     */
    data class BatchMessageResult(
        val status: String,
        val offset: Long
    )
}
