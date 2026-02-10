package com.agmtopy.kocketmq.logging.routeinfo

import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.ChannelEventListener
import io.netty.channel.Channel

/**
 * 处理Broker的链接
 */
class BrokerHousekeepingService(
    private val routeInfoManager: RouteInfoManager
) : ChannelEventListener {
    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME)
    }

    override fun onChannelConnect(remoteAddr: String?, channel: Channel?) {
        log.info("onChannelConnect: remoteAddr={}", remoteAddr)
    }

    override fun onChannelClose(remoteAddr: String?, channel: Channel?) {
        routeInfoManager.onChannelDestroy(remoteAddr, channel)
    }

    override fun onChannelException(remoteAddr: String?, channel: Channel?) {
        routeInfoManager.onChannelDestroy(remoteAddr, channel)
    }

    override fun onChannelIdle(remoteAddr: String?, channel: Channel?) {
        routeInfoManager.onChannelDestroy(remoteAddr, channel)
    }

}
