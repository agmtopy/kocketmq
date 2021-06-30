package com.agmtopy.kocketmq.logging.routeinfo

import com.agmtopy.kocketmq.remoting.ChannelEventListener
import io.netty.channel.Channel

/**
 * 处理Broker的链接
 */
class BrokerHousekeepingService: ChannelEventListener {
    override fun onChannelConnect(remoteAddr: String?, channel: Channel?) {
        TODO("Not yet implemented")
    }

    override fun onChannelClose(remoteAddr: String?, channel: Channel?) {
        TODO("Not yet implemented")
    }

    override fun onChannelException(remoteAddr: String?, channel: Channel?) {
        TODO("Not yet implemented")
    }

    override fun onChannelIdle(remoteAddr: String?, channel: Channel?) {
        TODO("Not yet implemented")
    }

}