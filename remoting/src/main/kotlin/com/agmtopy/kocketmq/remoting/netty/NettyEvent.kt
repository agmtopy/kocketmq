package com.agmtopy.kocketmq.remoting.netty

import io.netty.channel.Channel

class NettyEvent(type: NettyEventType, remoteAddr: String, channel: Channel) {
    private val type: NettyEventType
    val remoteAddr: String
    val channel: Channel

    init {
        this.type = type
        this.remoteAddr = remoteAddr
        this.channel = channel
    }

    fun getType(): NettyEventType {
        return type
    }

    override fun toString(): String {
        return "NettyEvent [type=$type, remoteAddr=$remoteAddr, channel=$channel]"
    }

}
