package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.store.GetMessageStatus
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.netty.NettyRequestProcessor
import com.agmtopy.kocketmq.remoting.protocol.RemotingSysResponseCode
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.runBlocking

/**
 * 消息查询处理器
 *
 * 处理消息查询请求：
 * - 根据msgId查询消息
 * - 根据key查询消息
 */
class QueryMessageProcessor(
    private val brokerController: BrokerController
) : NettyRequestProcessor {

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(QueryMessageProcessor::class.java)
    }

    override fun processRequest(ctx: ChannelHandlerContext?, request: RemotingCommand?): RemotingCommand? {
        if (ctx == null || request == null) {
            return RemotingCommand.createResponseCommand(RemotingSysResponseCode.SYSTEM_ERROR, "参数为空")
        }
        return when (request.code) {
            RequestCode.QUERY_MESSAGE -> queryMessage(request)
            RequestCode.VIEW_MESSAGE_BY_ID -> viewMessageById(request)
            else -> RemotingCommand.createResponseCommand(
                RemotingSysResponseCode.REQUEST_CODE_NOT_SUPPORTED,
                "不支持此请求码: ${request.code}"
            )
        }
    }

    override fun rejectRequest(): Boolean = false

    /**
     * 查询消息
     */
    private fun queryMessage(request: RemotingCommand): RemotingCommand? {
        return try {
            val topic = request.extFields?.get("topic")
            val key = request.extFields?.get("key")
            val maxNums = request.extFields?.get("maxNums")?.toInt() ?: 32

            if (topic.isNullOrBlank() || key.isNullOrBlank()) {
                return RemotingCommand.createResponseCommand(
                    RemotingSysResponseCode.SYSTEM_ERROR,
                    "Topic或Key不能为空"
                )
            }

            // TODO: 实现根据key查询消息
            // 需要建立索引：key -> offset

            log.info("查询消息: topic=$topic, key=$key, maxNums=$maxNums")

            val response = RemotingCommand.createResponseCommand(RemotingSysResponseCode.SUCCESS, "OK")
            response?.setBody("[]".toByteArray())

            response

        } catch (e: Exception) {
            log.error("查询消息失败", e)
            RemotingCommand.createResponseCommand(
                RemotingSysResponseCode.SYSTEM_ERROR,
                "查询失败: ${e.message}"
            )
        }
    }

    /**
     * 根据msgId查看消息
     */
    private fun viewMessageById(request: RemotingCommand): RemotingCommand? {
        return try {
            val msgId = request.extFields?.get("msgId")

            if (msgId.isNullOrBlank()) {
                return RemotingCommand.createResponseCommand(
                    RemotingSysResponseCode.SYSTEM_ERROR,
                    "msgId不能为空"
                )
            }

            log.info("查看消息: msgId=$msgId")

            val offset = msgId.toLongOrNull()

            if (offset == null) {
                return RemotingCommand.createResponseCommand(
                    RemotingSysResponseCode.SYSTEM_ERROR,
                    "无效的msgId"
                )
            }

            val result = runBlocking {
                brokerController.messageStore.getMessage("query", 0, offset)
            }

            if (result.status != GetMessageStatus.GET_OK || result.message == null) {
                return RemotingCommand.createResponseCommand(
                    RemotingSysResponseCode.SYSTEM_ERROR,
                    "消息不存在"
                )
            }

            val message = result.message
            val messageBody = message.body
            val response = RemotingCommand.createResponseCommand(RemotingSysResponseCode.SUCCESS, "OK")
            response?.setBody("topic=${message.topic}, body=${String(messageBody)}".toByteArray())

            response

        } catch (e: Exception) {
            log.error("查看消息失败", e)
            RemotingCommand.createResponseCommand(
                RemotingSysResponseCode.SYSTEM_ERROR,
                "查询失败: ${e.message}"
            )
        }
    }
}
