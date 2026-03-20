package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.store.MessageExt
import com.agmtopy.kocketmq.broker.store.PutMessageStatus
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.annotation.CFNullable
import com.agmtopy.kocketmq.remoting.netty.NettyRequestProcessor
import com.agmtopy.kocketmq.remoting.protocol.ResponseCode
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.runBlocking

/**
 * 发送消息请求处理器
 *
 * 处理请求码：
 * - SEND_MESSAGE (10)
 * - SEND_MESSAGE_V2 (310)
 * - SEND_BATCH_MESSAGE (320)
 */
class SendMessageProcessor(
    private val brokerController: BrokerController
) : NettyRequestProcessor {

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(SendMessageProcessor::class.java)
    }

    override fun processRequest(ctx: ChannelHandlerContext, request: RemotingCommand): RemotingCommand {
        val requestCode = request.code

        return when (requestCode) {
            RequestCode.SEND_MESSAGE, RequestCode.SEND_MESSAGE_V2 -> {
                processSendMessage(ctx, request)
            }
            RequestCode.SEND_BATCH_MESSAGE -> {
                processBatchSendMessage(ctx, request)
            }
            else -> {
                RemotingCommand.createResponseCommand(ResponseCode.REQUEST_CODE_NOT_SUPPORTED, "Unsupported request code: $requestCode")
            }
        }
    }

    /**
     * 处理发送消息请求
     */
    private fun processSendMessage(ctx: ChannelHandlerContext, request: RemotingCommand): RemotingCommand {
        return try {
            // 1. 解码请求头
            val requestHeader = decodeSendMessageRequestHeader(request)

            if (requestHeader == null) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "Decode request header failed"
                )
            }

            // 2. 构建消息
            val messageExt = buildMessageExt(requestHeader, request)

            // 3. 获取或创建Topic配置
            val topicConfig = brokerController.topicConfigManager.getTopicConfigOrCreate(requestHeader.topic)

            if (topicConfig == null) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.TOPIC_NOT_EXIST,
                    "Topic ${requestHeader.topic} not exist and auto create disabled"
                )
            }

            // 4. 存储消息
            val putResult = runBlocking {
                brokerController.messageStore.putMessage(messageExt)
            }

            // 5. 构建响应
            if (putResult.status == PutMessageStatus.PUT_OK) {
                val responseHeader = SendMessageResponseHeader(
                    msgId = "0",  // TODO: 实现msgId生成
                    queueId = messageExt.queueId,
                    queueOffset = 0L  // TODO: 从ConsumeQueue获取
                )

                val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
                response.setExtFields(responseHeader.toMap())
                response
            } else {
                RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "Put message failed: ${putResult.status}"
                )
            }

        } catch (e: Exception) {
            log.error("Process send message failed", e)
            RemotingCommand.createResponseCommand(ResponseCode.SYSTEM_ERROR, "Process failed: ${e.message}")
        }
    }

    /**
     * 处理批量发送消息请求
     */
    private fun processBatchSendMessage(ctx: ChannelHandlerContext, request: RemotingCommand): RemotingCommand {
        // TODO: 实现批量发送
        return RemotingCommand.createResponseCommand(
            ResponseCode.REQUEST_CODE_NOT_SUPPORTED,
            "Batch send not implemented yet"
        )
    }

    /**
     * 解码发送消息请求头
     */
    private fun decodeSendMessageRequestHeader(request: RemotingCommand): SendMessageRequestHeader? {
        return try {
            val extFields = request.extFields ?: return null

            SendMessageRequestHeader(
                topic = extFields["topic"] ?: "",
                queueId = extFields["queueId"]?.toInt() ?: 0,
                sysFlag = extFields["sysFlag"]?.toInt() ?: 0,
                bornTimestamp = extFields["bornTimestamp"]?.toLong() ?: System.currentTimeMillis(),
                flag = extFields["flag"]?.toInt() ?: 0,
                properties = extFields["properties"],
                reconsumeTimes = extFields["reconsumeTimes"]?.toInt() ?: 0,
                unitMode = extFields["unitMode"]?.toBoolean() ?: false
            )
        } catch (e: Exception) {
            log.error("Decode send message request header failed", e)
            null
        }
    }

    /**
     * 构建消息对象
     */
    private fun buildMessageExt(header: SendMessageRequestHeader, request: RemotingCommand): MessageExt {
        val body = request.body ?: ByteArray(0)

        return MessageExt(
            topic = header.topic,
            queueId = header.queueId,
            body = body,
            properties = header.properties,
            flag = header.flag,
            sysFlag = header.sysFlag,
            bodyCRC = MessageExt.calculateCRC32(body),
            bornTimestamp = header.bornTimestamp,
            storeTimestamp = System.currentTimeMillis(),
            queueOffset = 0  // 将由存储引擎设置
        )
    }
}

/**
 * 发送消息请求头
 */
data class SendMessageRequestHeader(
    @CFNotNull var topic: String,
    var queueId: Int = 0,
    var sysFlag: Int = 0,
    var bornTimestamp: Long = 0,
    var flag: Int = 0,
    @CFNullable var properties: String? = null,
    var reconsumeTimes: Int = 0,
    var unitMode: Boolean = false
)

/**
 * 发送消息响应头
 */
data class SendMessageResponseHeader(
    var msgId: String,
    var queueId: Int,
    var queueOffset: Long
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            "msgId" to msgId,
            "queueId" to queueId.toString(),
            "queueOffset" to queueOffset.toString()
        )
    }
}
