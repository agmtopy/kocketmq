package com.agmtopy.kocketmq.remoting

import io.netty.channel.Channel

interface ChannelEventListener {
    /**
     * 设置ChannelConnect
     */
    fun onChannelConnect(remoteAddr: String?, channel: Channel?)

    /**
     * 关闭Channel
     */
    fun onChannelClose(remoteAddr: String?, channel: Channel?)

    /**
     * 设置Channel exception
     */
    fun onChannelException(remoteAddr: String?, channel: Channel?)

    //TODO 含义待定
    fun onChannelIdle(remoteAddr: String?, channel: Channel?)
}