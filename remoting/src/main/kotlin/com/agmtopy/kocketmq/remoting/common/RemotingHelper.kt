package com.agmtopy.kocketmq.remoting.common

import io.netty.channel.Channel
import java.net.SocketAddress

class RemotingHelper{
    companion object{
        const val ROCKETMQ_REMOTING = "RocketmqRemoting"
        const val DEFAULT_CHARSET = "UTF-8"

        fun parseChannelRemoteAddr(channel: Channel?): String? {
            if (null == channel) {
                return ""
            }
            val remote = channel.remoteAddress()
            val addr = remote?.toString() ?: ""
            if (addr.length > 0) {
                val index = addr.lastIndexOf("/")
                return if (index >= 0) {
                    addr.substring(index + 1)
                } else addr
            }
            return ""
        }

        fun exceptionSimpleDesc(e: Throwable?): String? {
            val sb = StringBuffer()
            if (e != null) {
                sb.append(e.toString())
                val stackTrace = e.stackTrace
                if (stackTrace != null && stackTrace.size > 0) {
                    val elment = stackTrace[0]
                    sb.append(", ")
                    sb.append(elment.toString())
                }
            }
            return sb.toString()
        }

        fun parseSocketAddressAddr(socketAddress: SocketAddress?): String {
            if (socketAddress != null) {
                val addr = socketAddress.toString()
                if (addr.length > 0) {
                    return addr.substring(1)
                }
            }
            return ""
        }
    }
}