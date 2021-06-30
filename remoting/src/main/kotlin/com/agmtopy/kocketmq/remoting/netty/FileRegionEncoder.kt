package com.agmtopy.kocketmq.remoting.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.FileRegion
import io.netty.handler.codec.MessageToByteEncoder
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

class FileRegionEncoder : MessageToByteEncoder<FileRegion>() {
    /**
     * Encode a message into a [io.netty.buffer.ByteBuf]. This method will be called for each written message that
     * can be handled by this encoder.
     *
     * @param ctx the [io.netty.channel.ChannelHandlerContext] which this [ ] belongs to
     * @param msg the message to encode
     * @param out the [io.netty.buffer.ByteBuf] into which the encoded message will be written
     * @throws Exception is thrown if an error occurs
     */
    @Throws(Exception::class)
    override fun encode(ctx: ChannelHandlerContext, msg: FileRegion, out: ByteBuf) {
        val writableByteChannel: WritableByteChannel = object : WritableByteChannel {
            @Throws(IOException::class)
            override fun write(src: ByteBuffer): Int {
                out.writeBytes(src)
                return out.capacity()
            }

            override fun isOpen(): Boolean {
                return true
            }

            @Throws(IOException::class)
            override fun close() {
            }
        }
        val toTransfer = msg.count()
        while (true) {
            val transferred = msg.transfered()
            if (toTransfer - transferred <= 0) {
                break
            }
            msg.transferTo(writableByteChannel, transferred)
        }
    }
}