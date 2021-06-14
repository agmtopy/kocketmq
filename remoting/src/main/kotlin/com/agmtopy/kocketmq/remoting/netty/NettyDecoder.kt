package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.common.RemotingUtil
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.LengthFieldBasedFrameDecoder

/**
 * Netty Decoder
 */
class NettyDecoder : LengthFieldBasedFrameDecoder(FRAME_MAX_LENGTH, 0, 4, 0, 4) {
    @Throws(Exception::class)
    public override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf): Any? {
        var frame: ByteBuf? = null
        try {
            frame = super.decode(ctx, `in`) as ByteBuf
            if (null == frame) {
                return null
            }
            val byteBuffer = frame.nioBuffer()
            return RemotingCommand.decode(byteBuffer)
        } catch (e: Exception) {
            log.error("decode exception, " + RemotingHelper.parseChannelRemoteAddr(ctx.channel()), e)
            RemotingUtil.closeChannel(ctx.channel())
        } finally {
            frame?.release()
        }
        return null
    }

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)
        private val FRAME_MAX_LENGTH = System.getProperty("com.rocketmq.remoting.frameMaxLength", "16777216").toInt()
    }
}
