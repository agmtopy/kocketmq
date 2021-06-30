package com.agmtopy.kocketmq.remoting

import com.agmtopy.kocketmq.remoting.common.Pair
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingSendRequestException
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingTimeoutException
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingTooMuchRequestException
import com.agmtopy.kocketmq.remoting.netty.NettyRequestProcessor
import java.util.concurrent.ExecutorService

/**
 * 执行远程服务的具体实现
 */
interface RemotingServer : RemotingService {

    /**
     * 注册处理器
     */
    fun registerProcessor(
        requestCode: Int, processor: NettyRequestProcessor?, executor: ExecutorService?
    )

    /**
     * 注册默认处理器
     */
    fun registerDefaultProcessor(processor: NettyRequestProcessor?, executor: ExecutorService?)

    /**
     * 返回本地监听端口
     */
    fun localListenPort(): Int

    /**
     * 获取NettyRequestProcessor和ExecutorService
     */
    fun getProcessorPair(requestCode: Int): Pair<NettyRequestProcessor, ExecutorService>?

    /**
     * 同步执行
     */
    @Throws(InterruptedException::class, RemotingSendRequestException::class, RemotingTimeoutException::class)
    fun invokeSync(
        channel: io.netty.channel.Channel?, request: RemotingCommand?,
        timeoutMillis: Long
    ): RemotingCommand?

    /**
     * 异步执行
     */
    @Throws(
        InterruptedException::class,
        RemotingTooMuchRequestException::class,
        RemotingTimeoutException::class,
        RemotingSendRequestException::class
    )
    fun invokeAsync(
        channel: io.netty.channel.Channel?, request: RemotingCommand?, timeoutMillis: Long,
        invokeCallback: InvokeCallback?
    )

    /**
     * 仅执行一次
     */
    @Throws(
        InterruptedException::class,
        RemotingTooMuchRequestException::class,
        RemotingTimeoutException::class,
        RemotingSendRequestException::class
    )
    fun invokeOneway(channel: io.netty.channel.Channel?, request: RemotingCommand?, timeoutMillis: Long)

}