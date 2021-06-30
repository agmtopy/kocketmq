package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.common.concurrent.ServiceThread
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.ChannelEventListener
import com.agmtopy.kocketmq.remoting.InvokeCallback
import com.agmtopy.kocketmq.remoting.RPCHook
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.common.Pair
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.common.SemaphoreReleaseOnlyOnce
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingSendRequestException
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingTimeoutException
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingTooMuchRequestException
import com.agmtopy.kocketmq.remoting.protocol.RemotingCommandType
import com.agmtopy.kocketmq.remoting.protocol.RemotingSysResponseCode
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.ssl.SslContext
import java.util.*
import java.util.concurrent.*

/**
 * 对Netty的抽象
 */
abstract class NettyRemotingAbstract(permitsOneway: Int, permitsAsync: Int) {
    /**
     * 使用信号量限制正在进行的单向请求的最大数量，从而保护系统内存占用
     */
    private val semaphoreOneway: Semaphore

    /**
     * 使用信号量限制正在进行的异步请求的最大数量，从而保护系统内存占用
     */
    private val semaphoreAsync: Semaphore

    /**
     * 缓存所有正在进行的请求
     */
    private val responseTable: ConcurrentMap<Int, ResponseFuture> = ConcurrentHashMap(256)

    /**
     * 这个容器保存每个请求的所有处理器, 我们通过在这个Map中查找响应处理器来处理请求
     */
    val processorTable = HashMap<Int, Pair<NettyRequestProcessor?, ExecutorService?>>(64)

    /**
     * Netty处理器,定义类型为[ChannelEventListener].
     */
    internal val nettyEventExecutor = NettyEventExecutor()

    /**
     * 默认的[请求-处理器]
     */
    protected var defaultRequestProcessor: com.agmtopy.kocketmq.remoting.common.Pair<NettyRequestProcessor, ExecutorService>? =
        null

    /**
     * SSL context  [SslHandler].
     */
    @Volatile
    protected var sslContext: SslContext? = null

    /**
     * 自定义 rpc hooks
     */
    protected var rpcHooks: MutableList<RPCHook> = mutableListOf()

    companion object {
        /**
         * Remoting logger instance.
         */
        val log: InternalLogger = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)

        init {
            NettyLogger.initNettyLogger()
        }
    }

    /**
     * 自定义 channel event listener.
     */
    abstract var channelEventListener: ChannelEventListener?

    /**
     * 向Executor放入event事件
     */
    fun putNettyEvent(event: NettyEvent) {
        nettyEventExecutor.putNettyEvent(event)
    }

    /**
     * msg-command分为两种：request、response
     */
    @Throws(Exception::class)
    fun processMessageReceived(ctx: ChannelHandlerContext, msg: RemotingCommand?) {
        val cmd: RemotingCommand? = msg
        if (cmd != null) {
            when (cmd.type) {
                RemotingCommandType.REQUEST_COMMAND -> processRequestCommand(ctx, cmd)
                RemotingCommandType.RESPONSE_COMMAND -> processResponseCommand(ctx, cmd)
                else -> {
                }
            }
        }
    }

    protected fun doBeforeRpcHooks(addr: String?, request: RemotingCommand?) {
        if (rpcHooks.isNotEmpty()) {
            for (rpcHook: RPCHook in rpcHooks) {
                rpcHook.doBeforeRequest(addr, request)
            }
        }
    }

    protected fun doAfterRpcHooks(addr: String?, request: RemotingCommand?, response: RemotingCommand?) {
        if (rpcHooks.isNotEmpty()) {
            for (rpcHook: RPCHook in rpcHooks) {
                rpcHook.doAfterResponse(addr, request, response)
            }
        }
    }

    /**
     * Process incoming request command issued by remote peer.
     *
     * @param ctx channel handler context.
     * @param cmd request command.
     */
    fun processRequestCommand(ctx: ChannelHandlerContext, cmd: RemotingCommand) {
        val matched = processorTable[cmd.code]
        val pair = matched ?: defaultRequestProcessor
        val opaque: Int = cmd.opaque
        if (pair != null) {
            val run: Runnable = Runnable {
                try {
                    doBeforeRpcHooks(RemotingHelper.parseChannelRemoteAddr(ctx.channel()), cmd)
                    val callback: RemotingResponseCallback = object : RemotingResponseCallback {
                        override fun callback(response: RemotingCommand?) {
                            doAfterRpcHooks(RemotingHelper.parseChannelRemoteAddr(ctx.channel()), cmd, response)
                            if (!cmd.isOnewayRPC) {
                                if (response != null) {
                                    response.opaque = opaque
                                    response.markResponseType()
                                    try {
                                        ctx.writeAndFlush(response)
                                    } catch (e: Throwable) {
                                        NettyRemotingAbstract.Companion.log.error(
                                            "process request over, but response failed",
                                            e
                                        )
                                        NettyRemotingAbstract.Companion.log.error(cmd.toString())
                                        NettyRemotingAbstract.Companion.log.error(response.toString())
                                    }
                                } else {
                                }
                            }
                        }
                    }
                    if (pair.object1 is AsyncNettyRequestProcessor) {
                        val processor: AsyncNettyRequestProcessor = pair.object1 as AsyncNettyRequestProcessor
                        processor.asyncProcessRequest(ctx, cmd, callback)
                    } else {
                        val processor: NettyRequestProcessor = pair.object1
                        val response: RemotingCommand? = processor.processRequest(ctx, cmd)
                        callback.callback(response)
                    }
                } catch (e: Throwable) {
                    log.error("process request exception", e)
                    log.error(cmd.toString())
                    if (!cmd.isOnewayRPC) {
                        val response: RemotingCommand? = RemotingCommand.createResponseCommand(
                            RemotingSysResponseCode.SYSTEM_ERROR,
                            RemotingHelper.exceptionSimpleDesc(e)
                        )
                        response!!.opaque = opaque
                        ctx.writeAndFlush(response)
                    }
                }
            }
            if (pair.object1.rejectRequest()) {
                val response: RemotingCommand? = RemotingCommand.createResponseCommand(
                    RemotingSysResponseCode.SYSTEM_BUSY,
                    "[REJECTREQUEST]system busy, start flow control for a while"
                )
                response!!.opaque = opaque
                ctx.writeAndFlush(response)
                return
            }
            try {
                val requestTask = RequestTask(run, ctx.channel(), cmd)
                pair.object2.submit(requestTask)
            } catch (e: RejectedExecutionException) {
                if (System.currentTimeMillis() % 10000 == 0L) {
                    NettyRemotingAbstract.Companion.log.warn(
                        RemotingHelper.parseChannelRemoteAddr(ctx.channel())
                            .toString() + ", too many requests and system thread pool busy, RejectedExecutionException "
                                + pair.object2.toString()
                                + " request code: " + cmd.code
                    )
                }
                if (!cmd.isOnewayRPC) {
                    val response: RemotingCommand? = RemotingCommand.createResponseCommand(
                        RemotingSysResponseCode.SYSTEM_BUSY,
                        "[OVERLOAD]system busy, start flow control for a while"
                    )
                    response!!.opaque = opaque
                    ctx.writeAndFlush(response)
                }
            }
        } else {
            val error = " request type " + cmd!!.code.toString() + " not supported"
            val response: RemotingCommand? =
                RemotingCommand.createResponseCommand(RemotingSysResponseCode.REQUEST_CODE_NOT_SUPPORTED, error)
            response!!.opaque = opaque
            ctx.writeAndFlush(response)
            NettyRemotingAbstract.Companion.log.error(
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()).toString() + error
            )
        }
    }

    /**
     * Process response from remote peer to the previous issued requests.
     *
     * @param ctx channel handler context.
     * @param cmd response command instance.
     */
    fun processResponseCommand(ctx: ChannelHandlerContext, cmd: RemotingCommand) {
        val opaque: Int = cmd.opaque
        val responseFuture = responseTable[opaque]
        if (responseFuture != null) {
            responseFuture!!.responseCommand = cmd
            responseTable.remove(opaque)
            if (responseFuture!!.invokeCallback != null) {
                executeInvokeCallback(responseFuture)
            } else {
                responseFuture.putResponse(cmd)
                responseFuture.release()
            }
        } else {
            NettyRemotingAbstract.Companion.log.warn(
                "receive response, but not matched any request, " + RemotingHelper.parseChannelRemoteAddr(
                    ctx.channel()
                )
            )
            NettyRemotingAbstract.Companion.log.warn(cmd.toString())
        }
    }

    /**
     * Execute callback in callback executor. If callback executor is null, run directly in current thread
     */
    private fun executeInvokeCallback(responseFuture: ResponseFuture) {
        var runInThisThread = false
        val executor = callbackExecutor
        if (executor != null) {
            try {
                executor.submit(Runnable {
                    try {
                        responseFuture.executeInvokeCallback()
                    } catch (e: Throwable) {
                        NettyRemotingAbstract.Companion.log.warn(
                            "execute callback in executor exception, and callback throw",
                            e
                        )
                    } finally {
                        responseFuture.release()
                    }
                })
            } catch (e: Exception) {
                runInThisThread = true
                NettyRemotingAbstract.Companion.log.warn(
                    "execute callback in executor exception, maybe executor busy",
                    e
                )
            }
        } else {
            runInThisThread = true
        }
        if (runInThisThread) {
            try {
                responseFuture.executeInvokeCallback()
            } catch (e: Throwable) {
                NettyRemotingAbstract.Companion.log.warn("executeInvokeCallback Exception", e)
            } finally {
                responseFuture.release()
            }
        }
    }

    /**
     * Custom RPC hook.
     * Just be compatible with the previous version, use getRPCHooks instead.
     */
    @get:Deprecated("")
    protected val rPCHook: RPCHook?
        protected get() {
            return if (rpcHooks.size > 0) {
                rpcHooks.get(0)
            } else null
        }

    /**
     * Custom RPC hooks.
     *
     * @return RPC hooks if specified; null otherwise.
     */
    val rPCHooks: List<Any>
        get() = rpcHooks

    /**
     * This method specifies thread pool to use while invoking callback methods.
     *
     * @return Dedicated thread pool instance if specified; or null if the callback is supposed to be executed in the
     * netty event-loop thread.
     */
    abstract val callbackExecutor: ExecutorService?

    /**
     *
     *
     * This method is periodically invoked to scan and expire deprecated request.
     *
     */
    fun scanResponseTable() {
        val rfList: MutableList<ResponseFuture> = LinkedList()
        val it: MutableIterator<Map.Entry<Int, ResponseFuture>> = responseTable.entries.iterator()
        while (it.hasNext()) {
            val next = it.next()
            val rep = next.value
            if ((rep.beginTimestamp + rep.timeoutMillis + 1000) <= System.currentTimeMillis()) {
                rep.release()
                it.remove()
                rfList.add(rep)
                NettyRemotingAbstract.Companion.log.warn("remove timeout request, $rep")
            }
        }
        for (rf: ResponseFuture in rfList) {
            try {
                executeInvokeCallback(rf)
            } catch (e: Throwable) {
                NettyRemotingAbstract.Companion.log.warn("scanResponseTable, operationComplete Exception", e)
            }
        }
    }

    @Throws(InterruptedException::class, RemotingSendRequestException::class, RemotingTimeoutException::class)
    fun invokeSyncImpl(
        channel: Channel,
        request: RemotingCommand,
        timeoutMillis: Long
    ): RemotingCommand {
        val opaque: Int = request.opaque
        try {
            val responseFuture = ResponseFuture(channel, opaque, timeoutMillis, null, null)
            responseTable[opaque] = responseFuture
            val addr = channel.remoteAddress()
            channel.writeAndFlush(request).addListener(ChannelFutureListener { f ->
                if (f.isSuccess) {
                    responseFuture.sendRequestOK = true
                    return@ChannelFutureListener
                } else {
                    responseFuture.sendRequestOK = false
                }
                responseTable.remove(opaque)
                responseFuture.cause = f.cause()
                responseFuture.putResponse(null)
                NettyRemotingAbstract.Companion.log.warn("send a request command to channel <$addr> failed.")
            })
            val responseCommand: RemotingCommand = responseFuture.waitResponse(timeoutMillis)
                ?: if (responseFuture.sendRequestOK) {
                    throw RemotingTimeoutException(
                        RemotingHelper.parseSocketAddressAddr(addr),
                        timeoutMillis,
                        responseFuture.cause
                    )
                } else {
                    throw RemotingSendRequestException(
                        RemotingHelper.parseSocketAddressAddr(addr),
                        responseFuture.cause
                    )
                }
            return responseCommand
        } finally {
            responseTable.remove(opaque)
        }
    }

    @Throws(
        InterruptedException::class,
        RemotingTooMuchRequestException::class,
        RemotingTimeoutException::class,
        RemotingSendRequestException::class
    )
    fun invokeAsyncImpl(
        channel: Channel, request: RemotingCommand, timeoutMillis: Long,
        invokeCallback: InvokeCallback?
    ) {
        val beginStartTime = System.currentTimeMillis()
        val opaque: Int = request.opaque
        val acquired = semaphoreAsync.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)
        if (acquired) {
            val once = SemaphoreReleaseOnlyOnce(semaphoreAsync)
            val costTime = System.currentTimeMillis() - beginStartTime
            if (timeoutMillis < costTime) {
                once.release()
                throw RemotingTimeoutException("invokeAsyncImpl call timeout")
            }
            val responseFuture = ResponseFuture(channel, opaque, timeoutMillis - costTime, invokeCallback, once)
            responseTable[opaque] = responseFuture
            try {
                channel.writeAndFlush(request).addListener(ChannelFutureListener { f ->
                    if (f.isSuccess) {
                        responseFuture.sendRequestOK = true
                        return@ChannelFutureListener
                    }
                    requestFail(opaque)
                    NettyRemotingAbstract.Companion.log.warn(
                        "send a request command to channel <{}> failed.",
                        RemotingHelper.parseChannelRemoteAddr(channel)
                    )
                })
            } catch (e: Exception) {
                responseFuture.release()
                NettyRemotingAbstract.Companion.log.warn(
                    "send a request command to channel <" + RemotingHelper.parseChannelRemoteAddr(
                        channel
                    ).toString() + "> Exception", e
                )
                throw RemotingSendRequestException(RemotingHelper.parseChannelRemoteAddr(channel), e)
            }
        } else {
            if (timeoutMillis <= 0) {
                throw RemotingTooMuchRequestException("invokeAsyncImpl invoke too fast")
            } else {
                val info = String.format(
                    "invokeAsyncImpl tryAcquire semaphore timeout, %dms, waiting thread nums: %d semaphoreAsyncValue: %d",
                    timeoutMillis,
                    semaphoreAsync.queueLength,
                    semaphoreAsync.availablePermits()
                )
                NettyRemotingAbstract.Companion.log.warn(info)
                throw RemotingTimeoutException(info)
            }
        }
    }

    private fun requestFail(opaque: Int) {
        val responseFuture = responseTable.remove(opaque)
        if (responseFuture != null) {
            responseFuture.sendRequestOK = false
            responseFuture.putResponse(null)
            try {
                executeInvokeCallback(responseFuture)
            } catch (e: Throwable) {
                NettyRemotingAbstract.Companion.log.warn("execute callback in requestFail, and callback throw", e)
            } finally {
                responseFuture.release()
            }
        }
    }

    /**
     * mark the request of the specified channel as fail and to invoke fail callback immediately
     * @param channel the channel which is close already
     */
    protected fun failFast(channel: Channel?) {
        val it: Iterator<Map.Entry<Int, ResponseFuture>> = responseTable.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value!!.processChannel === channel) {
                val opaque: Int = entry.key
                if (opaque != null) {
                    requestFail(opaque)
                }
            }
        }
    }

    @Throws(
        InterruptedException::class,
        RemotingTooMuchRequestException::class,
        RemotingTimeoutException::class,
        RemotingSendRequestException::class
    )
    fun invokeOnewayImpl(channel: Channel, request: RemotingCommand, timeoutMillis: Long) {
        request.markOnewayRPC()
        val acquired = semaphoreOneway.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)
        if (acquired) {
            val once = SemaphoreReleaseOnlyOnce(semaphoreOneway)
            try {
                channel.writeAndFlush(request).addListener(ChannelFutureListener { f ->
                    once.release()
                    if (!f.isSuccess) {
                        NettyRemotingAbstract.Companion.log.warn("send a request command to channel <" + channel.remoteAddress() + "> failed.")
                    }
                })
            } catch (e: Exception) {
                once.release()
                NettyRemotingAbstract.Companion.log.warn("write send a request command to channel <" + channel.remoteAddress() + "> failed.")
                throw RemotingSendRequestException(RemotingHelper.parseChannelRemoteAddr(channel), e)
            }
        } else {
            if (timeoutMillis <= 0) {
                throw RemotingTooMuchRequestException("invokeOnewayImpl invoke too fast")
            } else {
                val info = String.format(
                    "invokeOnewayImpl tryAcquire semaphore timeout, %dms, waiting thread nums: %d semaphoreAsyncValue: %d",
                    timeoutMillis,
                    semaphoreOneway.queueLength,
                    semaphoreOneway.availablePermits()
                )
                NettyRemotingAbstract.Companion.log.warn(info)
                throw RemotingTimeoutException(info)
            }
        }
    }

    internal inner class NettyEventExecutor : ServiceThread() {
        private val eventQueue: LinkedBlockingQueue<NettyEvent> = LinkedBlockingQueue<NettyEvent>()
        private val maxSize = 10000
        fun putNettyEvent(event: NettyEvent) {
            if (eventQueue.size <= maxSize) {
                eventQueue.add(event)
            } else {
                NettyRemotingAbstract.Companion.log.warn(
                    "event queue size[{}] enough, so drop this event {}",
                    eventQueue.size,
                    event.toString()
                )
            }
        }

        override fun run() {
            NettyRemotingAbstract.Companion.log.info(serviceName + " service started")
            val listener: ChannelEventListener? = channelEventListener
            while (!this.isStopped) {
                try {
                    val event: NettyEvent? = eventQueue.poll(3000, TimeUnit.MILLISECONDS)
                    if (event != null && listener != null) {
                        when (event.getType()) {
                            NettyEventType.IDLE -> listener.onChannelIdle(event.remoteAddr, event.channel)
                            NettyEventType.CLOSE -> listener.onChannelClose(event.remoteAddr, event.channel)
                            NettyEventType.CONNECT -> listener.onChannelConnect(
                                event.remoteAddr,
                                event.channel
                            )
                            NettyEventType.EXCEPTION -> listener.onChannelException(
                                event.remoteAddr,
                                event.channel
                            )
                        }
                    }
                } catch (e: Exception) {
                    NettyRemotingAbstract.Companion.log.warn(serviceName + " service has exception. ", e)
                }
            }
            NettyRemotingAbstract.Companion.log.info(serviceName + " service end")
        }

        override val serviceName: String
            get() = NettyEventExecutor::class.java.simpleName
    }

    /**
     * Constructor, specifying capacity of one-way and asynchronous semaphores.
     *
     * @param permitsOneway Number of permits for one-way requests.
     * @param permitsAsync Number of permits for asynchronous requests.
     */
    init {
        semaphoreOneway = Semaphore(permitsOneway, true)
        semaphoreAsync = Semaphore(permitsAsync, true)
    }
}