package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.ChannelEventListener
import com.agmtopy.kocketmq.remoting.RPCHook
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.common.RemotingUtil
import com.agmtopy.kocketmq.remoting.common.TlsMode
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.concurrent.DefaultEventExecutorGroup
import java.io.IOException
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class NettyRemotingServer : NettyRemotingAbstract {
    private val log: InternalLogger = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)
    private var serverBootstrap: ServerBootstrap
    private var eventLoopGroupSelector: EventLoopGroup
    private var eventLoopGroupBoss: EventLoopGroup
    private var nettyServerConfig: NettyServerConfig

    private var publicExecutor: ExecutorService

    private val timer: Timer? = Timer("ServerHouseKeepingService", true)
    private var defaultEventExecutorGroup: DefaultEventExecutorGroup? = null


    private var port = 0

    private val HANDSHAKE_HANDLER_NAME = "handshakeHandler"
    private val TLS_HANDLER_NAME = "sslHandler"
    private val FILE_REGION_ENCODER_NAME = "fileRegionEncoder"

    // sharable handlers
    private var handshakeHandler: HandshakeHandler? = null
    private var encoder: NettyEncoder? = null
    private var connectionManageHandler: NettyConnectManageHandler? = null
    private var serverHandler: NettyServerHandler? = null

    /**
     * 辅助构造函数
     */
    constructor(nettyServerConfig: NettyServerConfig) : this(nettyServerConfig, null) {
    }

    /**
     * 辅助构造函数
     */
    constructor (nettyServerConfig: NettyServerConfig, channelEventListener: ChannelEventListener?) : super(
        nettyServerConfig.serverOnewaySemaphoreValue,
        nettyServerConfig.serverAsyncSemaphoreValue
    ) {
        serverBootstrap = ServerBootstrap()
        this.nettyServerConfig = nettyServerConfig
        this.channelEventListener = channelEventListener
        var publicThreadNums: Int = nettyServerConfig.getServerCallbackExecutorThreads()
        if (publicThreadNums <= 0) {
            publicThreadNums = 4
        }
        publicExecutor = Executors.newFixedThreadPool(publicThreadNums, object : ThreadFactory {
            private val threadIndex = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "NettyServerPublicExecutor_" + threadIndex.incrementAndGet())
            }
        })
        if (useEpoll()) {
            eventLoopGroupBoss = EpollEventLoopGroup(1, object : ThreadFactory {
                private val threadIndex = AtomicInteger(0)
                override fun newThread(r: Runnable): Thread {
                    return Thread(r, String.format("NettyEPOLLBoss_%d", threadIndex.incrementAndGet()))
                }
            })
            eventLoopGroupSelector =
                EpollEventLoopGroup(nettyServerConfig.getServerSelectorThreads(), object : ThreadFactory {
                    private val threadIndex = AtomicInteger(0)
                    private val threadTotal: Int = nettyServerConfig.getServerSelectorThreads()
                    override fun newThread(r: Runnable): Thread {
                        return Thread(
                            r,
                            String.format("NettyServerEPOLLSelector_%d_%d", threadTotal, threadIndex.incrementAndGet())
                        )
                    }
                })
        } else {
            eventLoopGroupBoss = NioEventLoopGroup(1, object : ThreadFactory {
                private val threadIndex = AtomicInteger(0)
                override fun newThread(r: Runnable): Thread {
                    return Thread(r, String.format("NettyNIOBoss_%d", threadIndex.incrementAndGet()))
                }
            })
            eventLoopGroupSelector =
                NioEventLoopGroup(nettyServerConfig.getServerSelectorThreads(), object : ThreadFactory {
                    private val threadIndex = AtomicInteger(0)
                    private val threadTotal: Int = nettyServerConfig.getServerSelectorThreads()
                    override fun newThread(r: Runnable): Thread {
                        return Thread(
                            r,
                            String.format("NettyServerNIOSelector_%d_%d", threadTotal, threadIndex.incrementAndGet())
                        )
                    }
                })
        }
        loadSslContext()
    }

    fun loadSslContext() {
        val tlsMode: TlsMode = TlsSystemConfig.tlsMode
        log.info("Server is running in TLS $tlsMode.name mode", )
        if (tlsMode !== TlsMode.DISABLED) {
            try {
                sslContext = TlsHelper.buildSslContext(false)
                log.info("SSLContext created for server")
            } catch (e: CertificateException) {
                log.error("Failed to create SSLContext for server", e)
            } catch (e: IOException) {
                log.error("Failed to create SSLContext for server", e)
            }
        }
    }

    private fun useEpoll(): Boolean {
        return (RemotingUtil.isLinuxPlatform
                && nettyServerConfig!!.isUseEpollNativeSelector()
                && Epoll.isAvailable())
    }

    fun start() {
        defaultEventExecutorGroup = DefaultEventExecutorGroup(
            nettyServerConfig!!.getServerWorkerThreads(),
            object : ThreadFactory {
                private val threadIndex = AtomicInteger(0)
                override fun newThread(r: Runnable): Thread {
                    return Thread(r, "NettyServerCodecThread_" + threadIndex.incrementAndGet())
                }
            })
        prepareSharableHandlers()
        val childHandler = serverBootstrap!!.group(eventLoopGroupBoss, eventLoopGroupSelector)
            .channel(if (useEpoll()) EpollServerSocketChannel::class.java else NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.SO_KEEPALIVE, false)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_SNDBUF, nettyServerConfig.getServerSocketSndBufSize())
            .childOption(ChannelOption.SO_RCVBUF, nettyServerConfig.getServerSocketRcvBufSize())
            .localAddress(InetSocketAddress(nettyServerConfig.getListenPort()))
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                @Throws(Exception::class)
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline()
                        .addLast(defaultEventExecutorGroup, HANDSHAKE_HANDLER_NAME, handshakeHandler)
                        .addLast(
                            defaultEventExecutorGroup,
                            encoder,
                            NettyDecoder(),
                            IdleStateHandler(0, 0, nettyServerConfig.getServerChannelMaxIdleTimeSeconds()),
                            connectionManageHandler,
                            serverHandler
                        )
                }
            })
        if (nettyServerConfig.isServerPooledByteBufAllocatorEnable()) {
            childHandler.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        }
        try {
            val sync = serverBootstrap.bind().sync()
            val addr = sync.channel().localAddress() as InetSocketAddress
            port = addr.port
        } catch (e1: InterruptedException) {
            throw RuntimeException("this.serverBootstrap.bind().sync() InterruptedException", e1)
        }
        if (channelEventListener != null) {
            nettyEventExecutor.start()
        }
        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    scanResponseTable()
                } catch (e: Throwable) {
                    log.error("scanResponseTable exception", e)
                }
            }
        }, (1000 * 3).toLong(), 1000)
    }

    fun shutdown() {
        try {
            if (timer != null) {
                timer.cancel()
            }
            eventLoopGroupBoss!!.shutdownGracefully()
            eventLoopGroupSelector!!.shutdownGracefully()
            if (nettyEventExecutor != null) {
                nettyEventExecutor.shutdown()
            }
            if (defaultEventExecutorGroup != null) {
                defaultEventExecutorGroup!!.shutdownGracefully()
            }
        } catch (e: Exception) {
            log.error("NettyRemotingServer shutdown exception, ", e)
        }
        if (publicExecutor != null) {
            try {
                publicExecutor.shutdown()
            } catch (e: Exception) {
                log.error("NettyRemotingServer shutdown exception, ", e)
            }
        }
    }

    fun registerRPCHook(rpcHook: RPCHook?) {
        if (rpcHook != null && !rpcHooks.contains(rpcHook)) {
            rpcHooks.all(rpcHook)
        }
    }

    fun registerProcessor(requestCode: Int, processor: NettyRequestProcessor, executor: ExecutorService?) {
        var executorThis = executor
        if (null == executor) {
            executorThis = publicExecutor
        }
        val pair = com.agmtopy.kocketmq.remoting.common.Pair(processor, executorThis!!)
        processorTable[requestCode] = pair
    }

    fun registerDefaultProcessor(processor: NettyRequestProcessor, executor: ExecutorService) {
        defaultRequestProcessor = com.agmtopy.kocketmq.remoting.common.Pair(processor, executor)
    }

    fun localListenPort(): Int {
        return port
    }

    fun getProcessorPair(requestCode: Int): Pair<NettyRequestProcessor?, ExecutorService?>? {
        return processorTable[requestCode]
    }

    @Throws(InterruptedException::class, RemotingSendRequestException::class, RemotingTimeoutException::class)
    fun invokeSync(channel: Channel?, request: RemotingCommand?, timeoutMillis: Long): RemotingCommand? {
        return invokeSyncImpl(channel!!, request, timeoutMillis)
    }

    @Throws(
        InterruptedException::class,
        RemotingTooMuchRequestException::class,
        RemotingTimeoutException::class,
        RemotingSendRequestException::class
    )
    fun invokeAsync(
        channel: Channel?,
        request: RemotingCommand?,
        timeoutMillis: Long,
        invokeCallback: InvokeCallback?
    ) {
        invokeAsyncImpl(channel!!, request, timeoutMillis, invokeCallback)
    }

    @Throws(
        InterruptedException::class,
        RemotingTooMuchRequestException::class,
        RemotingTimeoutException::class,
        RemotingSendRequestException::class
    )
    fun invokeOneway(channel: Channel?, request: RemotingCommand?, timeoutMillis: Long) {
        invokeOnewayImpl(channel!!, request, timeoutMillis)
    }

    fun getChannelEventListener(): ChannelEventListener? {
        return channelEventListener
    }


    fun getCallbackExecutor(): ExecutorService? {
        return publicExecutor
    }

    private fun prepareSharableHandlers() {
        handshakeHandler = HandshakeHandler(TlsSystemConfig.tlsMode)
        encoder = NettyEncoder()
        connectionManageHandler = NettyConnectManageHandler()
        serverHandler = NettyServerHandler()
    }

    @Sharable
    internal class HandshakeHandler(tlsMode: TlsMode) : SimpleChannelInboundHandler<ByteBuf>() {
        private val tlsMode: TlsMode

        @Throws(Exception::class)
        override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {

            // mark the current position so that we can peek the first byte to determine if the content is starting with
            // TLS handshake
            msg.markReaderIndex()
            val b = msg.getByte(0)
            if (b == HANDSHAKE_MAGIC_CODE) {
                when (tlsMode) {
                    DISABLED -> {
                        ctx.close()
                        log.warn("Clients intend to establish an SSL connection while this server is running in SSL disabled mode")
                    }
                    PERMISSIVE, ENFORCING -> if (null != sslContext) {
                        ctx.pipeline()
                            .addAfter(
                                defaultEventExecutorGroup,
                                HANDSHAKE_HANDLER_NAME,
                                TLS_HANDLER_NAME,
                                sslContext.newHandler(ctx.channel().alloc())
                            )
                            .addAfter(
                                defaultEventExecutorGroup,
                                TLS_HANDLER_NAME,
                                FILE_REGION_ENCODER_NAME,
                                FileRegionEncoder()
                            )
                        log.info("Handlers prepended to channel pipeline to establish SSL connection")
                    } else {
                        ctx.close()
                        log.error("Trying to establish an SSL connection but sslContext is null")
                    }
                    else -> log.warn("Unknown TLS mode")
                }
            } else if (tlsMode === TlsMode.ENFORCING) {
                ctx.close()
                log.warn("Clients intend to establish an insecure connection while this server is running in SSL enforcing mode")
            }

            // reset the reader index so that handshake negotiation may proceed as normal.
            msg.resetReaderIndex()
            try {
                // Remove this handler
                ctx.pipeline().remove(this)
            } catch (e: NoSuchElementException) {
                log.error("Error while removing HandshakeHandler", e)
            }

            // Hand over this message to the next .
            ctx.fireChannelRead(msg.retain())
        }

        companion object {
            private const val HANDSHAKE_MAGIC_CODE: Byte = 0x16
        }

        init {
            this.tlsMode = tlsMode
        }
    }

    @Sharable
    internal class NettyServerHandler : SimpleChannelInboundHandler<RemotingCommand?>() {
        @Throws(Exception::class)
        override fun channelRead0(ctx: ChannelHandlerContext, msg: RemotingCommand?) {
            processMessageReceived(ctx, msg)
        }
    }

    @Sharable
    internal class NettyConnectManageHandler : ChannelDuplexHandler() {
        @Throws(Exception::class)
        override fun channelRegistered(ctx: ChannelHandlerContext) {
            val remoteAddress: String = RemotingHelper.parseChannelRemoteAddr(ctx.channel())
            log.info("NETTY SERVER PIPELINE: channelRegistered {}", remoteAddress)
            super.channelRegistered(ctx)
        }

        @Throws(Exception::class)
        override fun channelUnregistered(ctx: ChannelHandlerContext) {
            val remoteAddress: String = RemotingHelper.parseChannelRemoteAddr(ctx.channel())
            log.info("NETTY SERVER PIPELINE: channelUnregistered, the channel[{}]", remoteAddress)
            super.channelUnregistered(ctx)
        }

        @Throws(Exception::class)
        override fun channelActive(ctx: ChannelHandlerContext) {
            val remoteAddress: String = RemotingHelper.parseChannelRemoteAddr(ctx.channel())
            log.info("NETTY SERVER PIPELINE: channelActive, the channel[{}]", remoteAddress)
            super.channelActive(ctx)
            if (this@NettyRemotingServer.channelEventListener != null) {
                this@NettyRemotingServer.putNettyEvent(NettyEvent(NettyEventType.CONNECT, remoteAddress, ctx.channel()))
            }
        }

        @Throws(Exception::class)
        override fun channelInactive(ctx: ChannelHandlerContext) {
            val remoteAddress: String = RemotingHelper.parseChannelRemoteAddr(ctx.channel())
            log.info("NETTY SERVER PIPELINE: channelInactive, the channel[{}]", remoteAddress)
            super.channelInactive(ctx)
            if (this@NettyRemotingServer.channelEventListener != null) {
                this@NettyRemotingServer.putNettyEvent(NettyEvent(NettyEventType.CLOSE, remoteAddress, ctx.channel()))
            }
        }

        @Throws(Exception::class)
        override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
            if (evt is IdleStateEvent) {
                if (evt.state() == IdleState.ALL_IDLE) {
                    val remoteAddress: String = RemotingHelper.parseChannelRemoteAddr(ctx.channel())
                    log.warn("NETTY SERVER PIPELINE: IDLE exception [{}]", remoteAddress)
                    RemotingUtil.closeChannel(ctx.channel())
                    if (this@NettyRemotingServer.channelEventListener != null) {
                        this@NettyRemotingServer
                            .putNettyEvent(NettyEvent(NettyEventType.IDLE, remoteAddress, ctx.channel()))
                    }
                }
            }
            ctx.fireUserEventTriggered(evt)
        }

        @Throws(Exception::class)
        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            val remoteAddress: String = RemotingHelper.parseChannelRemoteAddr(ctx.channel())
            log.warn("NETTY SERVER PIPELINE: exceptionCaught {}", remoteAddress)
            log.warn("NETTY SERVER PIPELINE: exceptionCaught exception.", cause)
            if (this@NettyRemotingServer.channelEventListener != null) {
                this@NettyRemotingServer.putNettyEvent(
                    NettyEvent(
                        NettyEventType.EXCEPTION,
                        remoteAddress,
                        ctx.channel()
                    )
                )
            }
            RemotingUtil.closeChannel(ctx.channel())
        }
    }

    override var channelEventListener: ChannelEventListener?
        get() = TODO("Not yet implemented")
        set(value) {}
    override val callbackExecutor: ExecutorService?
        get() = TODO("Not yet implemented")
}