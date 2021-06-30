package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.ChannelEventListener
import com.agmtopy.kocketmq.remoting.InvokeCallback
import com.agmtopy.kocketmq.remoting.RPCHook
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.common.Pair
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.common.SemaphoreReleaseOnlyOnce
import com.agmtopy.kocketmq.remoting.common.ServiceThread
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
     * Semaphore to limit maximum number of on-going one-way requests, which protects system memory footprint.
     */
    protected val semaphoreOneway: Semaphore

    /**
     * Semaphore to limit maximum number of on-going asynchronous requests, which protects system memory footprint.
     */
    protected val semaphoreAsync: Semaphore

    /**
     * This map caches all on-going requests.
     */
    protected val responseTable: ConcurrentMap<Int, ResponseFuture> = ConcurrentHashMap(256)

    /**
     * This container holds all processors per request code, aka, for each incoming request, we may look up the
     * responding processor in this map to handle the request.
     */
    protected val processorTable = HashMap<Int, Pair<NettyRequestProcessor, ExecutorService>>(64)

    /**
     * Executor to feed netty events to user defined [ChannelEventListener].
     */
    protected val nettyEventExecutor = NettyEventExecutor()

    /**
     * The default request processor to use in case there is no exact match in [.processorTable] per request code.
     */
    protected var defaultRequestProcessor: Pair<NettyRequestProcessor, ExecutorService>? = null

    /**
     * SSL context via which to create [SslHandler].
     */
    @Volatile
    protected var sslContext: SslContext? = null

    /**
     * custom rpc hooks
     */
    protected var rpcHooks: List<RPCHook> = ArrayList()

    companion object {
        /**
         * Remoting logger instance.
         */
        private val log = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)

        init {
            NettyLogger.initNettyLogger()
        }
    }

    /**
     * Custom channel event listener.
     *
     * @return custom channel event listener if defined; null otherwise.
     */
    abstract fun getChannelEventListener(): ChannelEventListener?

    /**
     * Put a netty event to the executor.
     *
     * @param event Netty event instance.
     */
    fun putNettyEvent(event: NettyEvent) {
        nettyEventExecutor.putNettyEvent(event)
    }

    /**
     * Entry of incoming command processing.
     *
     *
     *
     * **Note:**
     * The incoming remoting command may be
     *
     *  * An inquiry request from a remote peer component;
     *  * A response to a previous request issued by this very participant.
     *
     *
     *
     * @param ctx Channel handler context.
     * @param msg incoming remoting command.
     * @throws Exception if there were any error while processing the incoming command.
     */
    @Throws(Exception::class)
    fun processMessageReceived(ctx: ChannelHandlerContext, msg: RemotingCommand?) {
        val cmd = msg
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
        if (rpcHooks.size > 0) {
            for (rpcHook: RPCHook in rpcHooks) {
                rpcHook.doBeforeRequest(addr, request)
            }
        }
    }

    protected fun doAfterRpcHooks(addr: String?, request: RemotingCommand?, response: RemotingCommand?) {
        if (rpcHooks.size > 0) {
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
                                        log.error("process request over, but response failed", e)
                                        log.error(cmd.toString())
                                        log.error(response.toString())
                                    }
                                } else {
                                }
                            }
                        }
                    }
                    if (pair.object1 is AsyncNettyRequestProcessor) {
                        val processor = pair.object1 as AsyncNettyRequestProcessor
                        processor.asyncProcessRequest(ctx, cmd, callback)
                    } else {
                        val processor: NettyRequestProcessor = pair.object1
                        val response = processor.processRequest(ctx, cmd)
                        callback.callback(response)
                    }
                } catch (e: Throwable) {
                    log.error("process request exception", e)
                    log.error(cmd.toString())
                    if (!cmd.isOnewayRPC) {
                        val response = RemotingCommand.createResponseCommand(
                            RemotingSysResponseCode.SYSTEM_ERROR,
                            RemotingHelper.exceptionSimpleDesc(e)
                        )
                        response!!.opaque = opaque
                        ctx.writeAndFlush(response)
                    }
                }
            }
            if (pair.object1.rejectRequest()) {
                val response = RemotingCommand.createResponseCommand(
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
                    log.warn(
                        RemotingHelper.parseChannelRemoteAddr(ctx.channel())
                            .toString() + ", too many requests and system thread pool busy, RejectedExecutionException "
                                + pair.object2.toString()
                                + " request code: " + cmd.code
                    )
                }
                if (!cmd.isOnewayRPC) {
                    val response = RemotingCommand.createResponseCommand(
                        RemotingSysResponseCode.SYSTEM_BUSY,
                        "[OVERLOAD]system busy, start flow control for a while"
                    )
                    response!!.opaque = opaque
                    ctx.writeAndFlush(response)
                }
            }
        } else {
            val error = " request type " + cmd.code.toString() + " not supported"
            val response =
                RemotingCommand.createResponseCommand(RemotingSysResponseCode.REQUEST_CODE_NOT_SUPPORTED, error)
            response!!.opaque = opaque
            ctx.writeAndFlush(response)
            log.error(RemotingHelper.parseChannelRemoteAddr(ctx.channel()).toString() + error)
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
            responseFuture.responseCommand = cmd
            responseTable.remove(opaque)
            if (responseFuture.invokeCallback != null) {
                executeInvokeCallback(responseFuture)
            } else {
                responseFuture.putResponse(cmd)
                responseFuture.release()
            }
        } else {
            log.warn("receive response, but not matched any request, " + RemotingHelper.parseChannelRemoteAddr(ctx.channel()))
            log.warn(cmd.toString())
        }
    }

    /**
     * Execute callback in callback executor. If callback executor is null, run directly in current thread
     */
    private fun executeInvokeCallback(responseFuture: ResponseFuture) {
        var runInThisThread = false
        val executor = getCallbackExecutor()
        if (executor != null) {
            try {
                executor.submit(Runnable {
                    try {
                        responseFuture.executeInvokeCallback()
                    } catch (e: Throwable) {
                        log.warn("execute callback in executor exception, and callback throw", e)
                    } finally {
                        responseFuture.release()
                    }
                })
            } catch (e: Exception) {
                runInThisThread = true
                log.warn("execute callback in executor exception, maybe executor busy", e)
            }
        } else {
            runInThisThread = true
        }
        if (runInThisThread) {
            try {
                responseFuture.executeInvokeCallback()
            } catch (e: Throwable) {
                log.warn("executeInvokeCallback Exception", e)
            } finally {
                responseFuture.release()
            }
        }
    }

    /**
     * Custom RPC hook.
     * Just be compatible with the previous version, use getRPCHooks instead.
     */
    @Deprecated("")
    protected fun getRPCHook(): RPCHook? {
        return if (rpcHooks.size > 0) {
            rpcHooks.get(0)
        } else null
    }

    /**
     * Custom RPC hooks.
     *
     * @return RPC hooks if specified; null otherwise.
     */
    fun getRPCHooks(): List<RPCHook> {
        return rpcHooks
    }

    /**
     * This method specifies thread pool to use while invoking callback methods.
     *
     * @return Dedicated thread pool instance if specified; or null if the callback is supposed to be executed in the
     * netty event-loop thread.
     */
    abstract fun getCallbackExecutor(): ExecutorService?

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
                log.warn("remove timeout request, $rep")
            }
        }
        for (rf: ResponseFuture in rfList) {
            try {
                executeInvokeCallback(rf)
            } catch (e: Throwable) {
                log.warn("scanResponseTable, operationComplete Exception", e)
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
                log.warn("send a request command to channel <$addr> failed.")
            })
            val responseCommand = responseFuture.waitResponse(timeoutMillis)
                ?: if (responseFuture.sendRequestOK) {
                    throw RemotingTimeoutException(
                        RemotingHelper.parseSocketAddressAddr(addr), timeoutMillis,
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
                    log.warn(
                        "send a request command to channel <{}> failed.",
                        RemotingHelper.parseChannelRemoteAddr(channel)
                    )
                })
            } catch (e: Exception) {
                responseFuture.release()
                log.warn(
                    "send a request command to channel <" + RemotingHelper.parseChannelRemoteAddr(channel)
                        .toString() + "> Exception", e
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
                log.warn(info)
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
                log.warn("execute callback in requestFail, and callback throw", e)
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
            if (entry.value.processChannel === channel) {
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
                        log.warn("send a request command to channel <" + channel.remoteAddress() + "> failed.")
                    }
                })
            } catch (e: Exception) {
                once.release()
                log.warn("write send a request command to channel <" + channel.remoteAddress() + "> failed.")
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
                log.warn(info)
                throw RemotingTimeoutException(info)
            }
        }
    }

    inner class NettyEventExecutor() : ServiceThread() {
        private val eventQueue = LinkedBlockingQueue<NettyEvent>()
        private val maxSize = 10000
        fun putNettyEvent(event: NettyEvent) {
            if (eventQueue.size <= maxSize) {
                eventQueue.add(event)
            } else {
                log.warn("event queue size[{}] enough, so drop this event {}", eventQueue.size, event.toString())
            }
        }

        override fun run() {
            log.info(serviceName + " service started")
            val listener = getChannelEventListener()
            while (!this.isStopped) {
                try {
                    val event = eventQueue.poll(3000, TimeUnit.MILLISECONDS)
                    if (event != null && listener != null) {
                        when (event.getType()) {
                            NettyEventType.IDLE -> listener.onChannelIdle(event.remoteAddr, event.channel)
                            NettyEventType.CLOSE -> listener.onChannelClose(event.remoteAddr, event.channel)
                            NettyEventType.CONNECT -> listener.onChannelConnect(event.remoteAddr, event.channel)
                            NettyEventType.EXCEPTION -> listener.onChannelException(event.remoteAddr, event.channel)
                            else -> {
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.warn(serviceName + " service has exception. ", e)
                }
            }
            log.info(serviceName + " service end")
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
