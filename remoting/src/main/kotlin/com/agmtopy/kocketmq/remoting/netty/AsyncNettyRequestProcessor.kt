package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.remoting.RemotingCommand
import io.netty.channel.ChannelHandlerContext

abstract class AsyncNettyRequestProcessor : NettyRequestProcessor {
    @Throws(Exception::class)
    fun asyncProcessRequest(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand?,
        responseCallback: RemotingResponseCallback
    ) {
        val response: RemotingCommand? = processRequest(ctx, request)
        responseCallback.callback(response)
    }
}