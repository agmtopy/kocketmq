package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.remoting.constant.NettySystemConfig

/**
 * NettyServer Config
 */
class NettyServerConfig: Cloneable {
    private var listenPort = 8888
    private var serverWorkerThreads = 8
    private var serverCallbackExecutorThreads = 0
    private var serverSelectorThreads = 3
    var serverOnewaySemaphoreValue = 256
    var serverAsyncSemaphoreValue = 64
    private var serverChannelMaxIdleTimeSeconds = 120

    private var serverSocketSndBufSize: Int = NettySystemConfig.socketSndbufSize
    private var serverSocketRcvBufSize: Int = NettySystemConfig.socketRcvbufSize
    private var serverPooledByteBufAllocatorEnable = true

    /**
     * make make install
     *
     *
     * ../glibc-2.10.1/configure \ --prefix=/usr \ --with-headers=/usr/include \
     * --host=x86_64-linux-gnu \ --build=x86_64-pc-linux-gnu \ --without-gd
     */
    private var useEpollNativeSelector = false

    fun getListenPort(): Int {
        return listenPort
    }

    fun setListenPort(listenPort: Int) {
        this.listenPort = listenPort
    }

    fun getServerWorkerThreads(): Int {
        return serverWorkerThreads
    }

    fun setServerWorkerThreads(serverWorkerThreads: Int) {
        this.serverWorkerThreads = serverWorkerThreads
    }

    fun getServerSelectorThreads(): Int {
        return serverSelectorThreads
    }

    fun setServerSelectorThreads(serverSelectorThreads: Int) {
        this.serverSelectorThreads = serverSelectorThreads
    }

    fun getServerOnewaySemaphoreValue(): Int {
        return serverOnewaySemaphoreValue
    }

    fun setServerOnewaySemaphoreValue(serverOnewaySemaphoreValue: Int) {
        this.serverOnewaySemaphoreValue = serverOnewaySemaphoreValue
    }

    fun getServerCallbackExecutorThreads(): Int {
        return serverCallbackExecutorThreads
    }

    fun setServerCallbackExecutorThreads(serverCallbackExecutorThreads: Int) {
        this.serverCallbackExecutorThreads = serverCallbackExecutorThreads
    }

    fun getServerAsyncSemaphoreValue(): Int {
        return serverAsyncSemaphoreValue
    }

    fun setServerAsyncSemaphoreValue(serverAsyncSemaphoreValue: Int) {
        this.serverAsyncSemaphoreValue = serverAsyncSemaphoreValue
    }

    fun getServerChannelMaxIdleTimeSeconds(): Int {
        return serverChannelMaxIdleTimeSeconds
    }

    fun setServerChannelMaxIdleTimeSeconds(serverChannelMaxIdleTimeSeconds: Int) {
        this.serverChannelMaxIdleTimeSeconds = serverChannelMaxIdleTimeSeconds
    }

    fun getServerSocketSndBufSize(): Int {
        return serverSocketSndBufSize
    }

    fun setServerSocketSndBufSize(serverSocketSndBufSize: Int) {
        this.serverSocketSndBufSize = serverSocketSndBufSize
    }

    fun getServerSocketRcvBufSize(): Int {
        return serverSocketRcvBufSize
    }

    fun setServerSocketRcvBufSize(serverSocketRcvBufSize: Int) {
        this.serverSocketRcvBufSize = serverSocketRcvBufSize
    }

    fun isServerPooledByteBufAllocatorEnable(): Boolean {
        return serverPooledByteBufAllocatorEnable
    }

    fun setServerPooledByteBufAllocatorEnable(serverPooledByteBufAllocatorEnable: Boolean) {
        this.serverPooledByteBufAllocatorEnable = serverPooledByteBufAllocatorEnable
    }

    fun isUseEpollNativeSelector(): Boolean {
        return useEpollNativeSelector
    }

    fun setUseEpollNativeSelector(useEpollNativeSelector: Boolean) {
        this.useEpollNativeSelector = useEpollNativeSelector
    }

    @Throws(CloneNotSupportedException::class)
    override fun clone(): Any {
        return super.clone() as NettyServerConfig
    }
}