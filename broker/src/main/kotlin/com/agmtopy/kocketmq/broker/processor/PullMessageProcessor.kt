package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.netty.NettyRequestProcessor
import com.agmtopy.kocketmq.remoting.protocol.ResponseCode
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.runBlocking

/**
 * 拉取消息请求处理器
 *
 * 处理请求码：PULL_MESSAGE (11)
 */
class PullMessageProcessor(
    private val brokerController: BrokerController
) : NettyRequestProcessor {

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(PullMessageProcessor::class.java)
    }

    override fun processRequest(ctx: ChannelHandlerContext, request: RemotingCommand): RemotingCommand {
        return try {
            // 1. 解码请求头
            val requestHeader = decodePullMessageRequestHeader(request)

            if (requestHeader == null) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "Decode request header failed"
                )
            }

            // 2. 检查Topic是否存在
            val topicConfig = brokerController.topicConfigManager.getTopicConfig(requestHeader.topic)

            if (topicConfig == null) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.TOPIC_NOT_EXIST,
                    "Topic ${requestHeader.topic} not exist"
                )
            }

            // 3. 从存储引擎拉取消息
            val messages = runBlocking {
                brokerController.messageStore.getMessages(
                    topic = requestHeader.topic,
                    queueId = requestHeader.queueId,
                    startLogicOffset = requestHeader.queueOffset,
                    maxNums = requestHeader.maxMsgNums
                )
            }

            // 4. 构建响应
            val responseHeader = PullMessageResponseHeader(
                suggestWhichBrokerId = brokerController.getBrokerId(),
                nextBeginOffset = requestHeader.queueOffset + messages.size,
                minOffset = runBlocking { brokerController.messageStore.getMinOffset() },
                maxOffset = runBlocking { brokerController.messageStore.getMaxOffset() }
            )

            val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
            response.setExtFields(responseHeader.toMap())

            // TODO: 序列化消息到响应body
            // response.body = serializeMessages(messages)

            response

        } catch (e: Exception) {
            log.error("Process pull message failed", e)
            RemotingCommand.createResponseCommand(ResponseCode.SYSTEM_ERROR, "Process failed: ${e.message}")
        }
    }

    /**
     * 解码拉取消息请求头
     */
    private fun decodePullMessageRequestHeader(request: RemotingCommand): PullMessageRequestHeader? {
        return try {
            val extFields = request.extFields ?: return null

            PullMessageRequestHeader(
                consumerGroup = extFields["consumerGroup"] ?: "",
                topic = extFields["topic"] ?: "",
                queueId = extFields["queueId"]?.toInt() ?: 0,
                queueOffset = extFields["queueOffset"]?.toLong() ?: 0,
                maxMsgNums = extFields["maxMsgNums"]?.toInt() ?: 32,
                sysFlag = extFields["sysFlag"]?.toInt() ?: 0,
                commitOffset = extFields["commitOffset"]?.toLong() ?: 0,
                suspendTimeoutMillis = extFields["suspendTimeoutMillis"]?.toLong() ?: 0
            )
        } catch (e: Exception) {
            log.error("Decode pull message request header failed", e)
            null
        }
    }
}

/**
 * 拉取消息请求头
 */
data class PullMessageRequestHeader(
    @CFNotNull var consumerGroup: String,
    @CFNotNull var topic: String,
    var queueId: Int = 0,
    var queueOffset: Long = 0,
    var maxMsgNums: Int = 32,
    var sysFlag: Int = 0,
    var commitOffset: Long = 0,
    var suspendTimeoutMillis: Long = 0
)

/**
 * 拉取消息响应头
 */
data class PullMessageResponseHeader(
    var suggestWhichBrokerId: Long,
    var nextBeginOffset: Long,
    var minOffset: Long,
    var maxOffset: Long
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            "suggestWhichBrokerId" to suggestWhichBrokerId.toString(),
            "nextBeginOffset" to nextBeginOffset.toString(),
            "minOffset" to minOffset.toString(),
            "maxOffset" to maxOffset.toString()
        )
    }
}
