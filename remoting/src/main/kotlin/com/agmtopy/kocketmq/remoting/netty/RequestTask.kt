package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.remoting.RemotingCommand
import io.netty.channel.Channel


class RequestTask(private val runnable: Runnable?, private val channel: Channel?, request: RemotingCommand?) :
    Runnable {
    val createTimestamp = System.currentTimeMillis()
    private val request: RemotingCommand?
    var isStopRun = false

    override fun hashCode(): Int {
        var result = runnable?.hashCode() ?: 0
        result = 31 * result + (createTimestamp xor (createTimestamp ushr 32)).toInt()
        result = 31 * result + (channel?.hashCode() ?: 0)
        result = 31 * result + if (request != null) request.hashCode() else 0
        result = 31 * result + if (isStopRun) 1 else 0
        return result
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is RequestTask) return false
        val that = o
        if (createTimestamp != that.createTimestamp) return false
        if (isStopRun != that.isStopRun) return false
        if (if (channel != null) channel != that.channel else that.channel != null) return false
        return if (request != null) request.opaque === that.request!!.opaque else that.request == null
    }

    override fun run() {
        if (!isStopRun) runnable!!.run()
    }

    fun returnResponse(code: Int, remark: String?) {
        val response: RemotingCommand? = RemotingCommand.createResponseCommand(code, remark)
        response!!.opaque = request!!.opaque
        channel!!.writeAndFlush(response)
    }

    init {
        this.request = request
    }
}