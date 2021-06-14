package com.agmtopy.kocketmq.remoting.common

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import java.io.IOException
import java.net.*
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider
import java.util.*

object RemotingUtil {
    val OS_NAME = System.getProperty("os.name")
    private val log: InternalLogger = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)
    var isLinuxPlatform = false
        private set
    var isWindowsPlatform = false
        private set

    @Throws(IOException::class)
    fun openSelector(): Selector? {
        var result: Selector? = null
        if (isLinuxPlatform) {
            try {
                val providerClazz = Class.forName("sun.nio.ch.EPollSelectorProvider")
                if (providerClazz != null) {
                    try {
                        val method = providerClazz.getMethod("provider")
                        if (method != null) {
                            val selectorProvider = method.invoke(null) as SelectorProvider
                            if (selectorProvider != null) {
                                result = selectorProvider.openSelector()
                            }
                        }
                    } catch (e: Exception) {
                        log.warn("Open ePoll Selector for linux platform exception", e)
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        if (result == null) {
            result = Selector.open()
        }
        return result
    }

    // Traversal Network interface to get the first non-loopback and non-private address
    val localAddress:

    // prefer ipv4
    //If failed to find,fall back to localhost
            String?
        get() {
            try {
                // Traversal Network interface to get the first non-loopback and non-private address
                val enumeration = NetworkInterface.getNetworkInterfaces()
                val ipv4Result = ArrayList<String>()
                val ipv6Result = ArrayList<String>()
                while (enumeration.hasMoreElements()) {
                    val networkInterface = enumeration.nextElement()
                    val en = networkInterface.inetAddresses
                    while (en.hasMoreElements()) {
                        val address = en.nextElement()
                        if (!address.isLoopbackAddress) {
                            if (address is Inet6Address) {
                                ipv6Result.add(normalizeHostAddress(address))
                            } else {
                                ipv4Result.add(normalizeHostAddress(address))
                            }
                        }
                    }
                }

                // prefer ipv4
                if (!ipv4Result.isEmpty()) {
                    for (ip in ipv4Result) {
                        if (ip.startsWith("127.0") || ip.startsWith("192.168")) {
                            continue
                        }
                        return ip
                    }
                    return ipv4Result[ipv4Result.size - 1]
                } else if (!ipv6Result.isEmpty()) {
                    return ipv6Result[0]
                }
                //If failed to find,fall back to localhost
                val localHost = InetAddress.getLocalHost()
                return normalizeHostAddress(localHost)
            } catch (e: Exception) {
                log.error("Failed to obtain local address", e)
            }
            return null
        }

    fun normalizeHostAddress(localHost: InetAddress): String {
        return if (localHost is Inet6Address) {
            "[" + localHost.getHostAddress() + "]"
        } else {
            localHost.hostAddress
        }
    }

    fun string2SocketAddress(addr: String): SocketAddress {
        val split = addr.lastIndexOf(":")
        val host = addr.substring(0, split)
        val port = addr.substring(split + 1)
        return InetSocketAddress(host, port.toInt())
    }

    fun socketAddress2String(addr: SocketAddress): String {
        val sb = StringBuilder()
        val inetSocketAddress = addr as InetSocketAddress
        sb.append(inetSocketAddress.address.hostAddress)
        sb.append(":")
        sb.append(inetSocketAddress.port)
        return sb.toString()
    }

    @JvmOverloads
    fun connect(remote: SocketAddress?, timeoutMillis: Int = 1000 * 5): SocketChannel? {
        var sc: SocketChannel? = null
        try {
            sc = SocketChannel.open()
            sc.configureBlocking(true)
            sc.socket().setSoLinger(false, -1)
            sc.socket().tcpNoDelay = true
            sc.socket().receiveBufferSize = 1024 * 64
            sc.socket().sendBufferSize = 1024 * 64
            sc.socket().connect(remote, timeoutMillis)
            sc.configureBlocking(false)
            return sc
        } catch (e: Exception) {
            if (sc != null) {
                try {
                    sc.close()
                } catch (e1: IOException) {
                    e1.printStackTrace()
                }
            }
        }
        return null
    }

    fun closeChannel(channel: Channel) {
        val addrRemote = RemotingHelper.parseChannelRemoteAddr(channel)
        channel.close().addListener(ChannelFutureListener { future ->
            log.info(
                "closeChannel: close the connection to remote address[{}] result: {}", addrRemote,
                future.isSuccess
            )
        })
    }

    init {
        if (OS_NAME != null && OS_NAME.toLowerCase().contains("linux")) {
            isLinuxPlatform = true
        }
        if (OS_NAME != null && OS_NAME.toLowerCase().contains("windows")) {
            isWindowsPlatform = true
        }
    }
}