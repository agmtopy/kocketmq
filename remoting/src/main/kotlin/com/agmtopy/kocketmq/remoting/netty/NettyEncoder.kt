package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.common.RemotingUtil
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import java.nio.ByteBuffer

@Sharable
class NettyEncoder : MessageToByteEncoder<RemotingCommand?>() {
    @Throws(Exception::class)
    public override fun encode(ctx: ChannelHandlerContext, remotingCommand: RemotingCommand?, out: ByteBuf) {
        try {
            val header: ByteBuffer = remotingCommand!!.encodeHeader()
            out.writeBytes(header)
            val body: ByteArray? = remotingCommand.getBody()
            if (body != null) {
                out.writeBytes(body)
            }
        } catch (e: Exception) {
            log.error("encode exception, " + RemotingHelper.parseChannelRemoteAddr(ctx.channel()), e)
            if (remotingCommand != null) {
                log.error(remotingCommand.toString())
            }
            RemotingUtil.closeChannel(ctx.channel())
        }
    }

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)
    }
}