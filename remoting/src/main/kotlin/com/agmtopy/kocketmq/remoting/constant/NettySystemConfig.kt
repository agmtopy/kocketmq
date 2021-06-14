package com.agmtopy.kocketmq.remoting.constant

import java.lang.Boolean

object NettySystemConfig {
    const val COM_ROCKETMQ_REMOTING_NETTY_POOLED_BYTE_BUF_ALLOCATOR_ENABLE =
        "com.rocketmq.remoting.nettyPooledByteBufAllocatorEnable"
    const val COM_ROCKETMQ_REMOTING_SOCKET_SNDBUF_SIZE = "com.rocketmq.remoting.socket.sndbuf.size"
    const val COM_ROCKETMQ_REMOTING_SOCKET_RCVBUF_SIZE = "com.rocketmq.remoting.socket.rcvbuf.size"
    const val COM_ROCKETMQ_REMOTING_CLIENT_ASYNC_SEMAPHORE_VALUE = "com.rocketmq.remoting.clientAsyncSemaphoreValue"
    const val COM_ROCKETMQ_REMOTING_CLIENT_ONEWAY_SEMAPHORE_VALUE = "com.rocketmq.remoting.clientOnewaySemaphoreValue"
    val NETTY_POOLED_BYTE_BUF_ALLOCATOR_ENABLE =  //
        Boolean.parseBoolean(System.getProperty(COM_ROCKETMQ_REMOTING_NETTY_POOLED_BYTE_BUF_ALLOCATOR_ENABLE, "false"))
    val CLIENT_ASYNC_SEMAPHORE_VALUE =
        System.getProperty(COM_ROCKETMQ_REMOTING_CLIENT_ASYNC_SEMAPHORE_VALUE, "65535").toInt()
    val CLIENT_ONEWAY_SEMAPHORE_VALUE =
        System.getProperty(COM_ROCKETMQ_REMOTING_CLIENT_ONEWAY_SEMAPHORE_VALUE, "65535").toInt()
    var socketSndbufSize = System.getProperty(COM_ROCKETMQ_REMOTING_SOCKET_SNDBUF_SIZE, "65535").toInt()
    var socketRcvbufSize = System.getProperty(COM_ROCKETMQ_REMOTING_SOCKET_RCVBUF_SIZE, "65535").toInt()
}