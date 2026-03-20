package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.netty.NettyRequestProcessor
import com.agmtopy.kocketmq.remoting.protocol.ResponseCode
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

    override fun processRequest(ctx: ChannelHandlerContext, request: RemotingCommand): RemotingCommand {
        return when (request.code) {
            RequestCode.QUERY_MESSAGE -> queryMessage(request)
            RequestCode.VIEW_MESSAGE_BY_ID -> viewMessageById(request)
            else -> RemotingCommand.createResponseCommand(
                ResponseCode.REQUEST_CODE_NOT_SUPPORTED,
                "不支持此请求码: ${request.code}"
            )
        }
    }

    /**
     * 查询消息
     */
    private fun queryMessage(request: RemotingCommand): RemotingCommand {
        return try {
            val topic = request.extFields?.get("topic")
            val key = request.extFields?.get("key")
            val maxNums = request.extFields?.get("maxNums")?.toInt() ?: 32

            if (topic.isNullOrBlank() || key.isNullOrBlank()) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "Topic或Key不能为空"
                )
            }

            // TODO: 实现根据key查询消息
            // 需要建立索引：key -> offset

            log.info("查询消息: topic={}, key={}, maxNums={}", topic, key, maxNums)

            val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
            response.body = "[]".toByteArray()  // TODO: 返回消息列表

            response

        } catch (e: Exception) {
            log.error("查询消息失败", e)
            RemotingCommand.createResponseCommand(
                ResponseCode.SYSTEM_ERROR,
                "查询失败: ${e.message}"
            )
        }
    }

    /**
     * 根据msgId查看消息
     */
    private fun viewMessageById(request: RemotingCommand): RemotingCommand {
        return try {
            val msgId = request.extFields?.get("msgId")

            if (msgId.isNullOrBlank()) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "msgId不能为空"
                )
            }

            // TODO: 实现根据msgId查询
            // msgId格式：offset@size 或唯一ID

            log.info("查看消息: msgId={}", msgId)

            // 解析offset（假设msgId就是offset）
            val offset = msgId.toLongOrNull()

            if (offset == null) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "无效的msgId"
                )
            }

            val message = runBlocking {
                brokerController.messageStore.getMessage("query", 0, offset)
            }

            if (message == null) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "消息不存在"
                )
            }

            // TODO: 序列化消息
            val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
            response.body = "topic=${message.topic}, body=${String(message.body)}".toByteArray()

            response

        } catch (e: Exception) {
            log.error("查看消息失败", e)
            RemotingCommand.createResponseCommand(
                ResponseCode.SYSTEM_ERROR,
                "查询失败: ${e.message}"
            )
        }
    }
}
