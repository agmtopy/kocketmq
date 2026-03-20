package com.agmtopy.kocketmq.broker.client

import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.protocol.ResponseCode

/**
 * 简单的测试客户端
 *
 * 用于集成测试，模拟发送和拉取消息
 */
class TestClient {

    /**
     * 构建发送消息请求
     */
    fun buildSendMessageRequest(
        topic: String,
        queueId: Int = 0,
        body: ByteArray
    ): RemotingCommand {
        val request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, null)
        request.extFields = mapOf(
            "topic" to topic,
            "queueId" to queueId.toString(),
            "sysFlag" to "0",
            "bornTimestamp" to System.currentTimeMillis().toString(),
            "flag" to "0"
        )
        request.body = body
        return request
    }

    /**
     * 构建拉取消息请求
     */
    fun buildPullMessageRequest(
        consumerGroup: String,
        topic: String,
        queueId: Int = 0,
        queueOffset: Long = 0,
        maxMsgNums: Int = 32
    ): RemotingCommand {
        val request = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, null)
        request.extFields = mapOf(
            "consumerGroup" to consumerGroup,
            "topic" to topic,
            "queueId" to queueId.toString(),
            "queueOffset" to queueOffset.toString(),
            "maxMsgNums" to maxMsgNums.toString()
        )
        return request
    }

    /**
     * 验证发送消息响应
     */
    fun verifySendMessageResponse(response: RemotingCommand): Boolean {
        return response.code == ResponseCode.SUCCESS &&
                response.extFields?.containsKey("msgId") == true &&
                response.extFields?.containsKey("queueId") == true &&
                response.extFields?.containsKey("queueOffset") == true
    }

    /**
     * 验证拉取消息响应
     */
    fun verifyPullMessageResponse(response: RemotingCommand): Boolean {
        return response.code == ResponseCode.SUCCESS &&
                response.extFields?.containsKey("nextBeginOffset") == true &&
                response.extFields?.containsKey("minOffset") == true &&
                response.extFields?.containsKey("maxOffset") == true
    }
}
