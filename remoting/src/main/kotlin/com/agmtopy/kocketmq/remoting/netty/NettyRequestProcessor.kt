package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.remoting.RemotingCommand
import io.netty.channel.ChannelHandlerContext

/**
 * Netty请求处理器
 */
interface NettyRequestProcessor {
    /**
     * 处理请求
     */
    @Throws(Exception::class)
    fun processRequest(ctx: ChannelHandlerContext?, request: RemotingCommand?): RemotingCommand?

    /**
     * 拒绝请求
     */
    fun rejectRequest(): Boolean

}