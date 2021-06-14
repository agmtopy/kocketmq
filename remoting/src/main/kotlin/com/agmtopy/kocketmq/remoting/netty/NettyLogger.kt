package com.agmtopy.kocketmq.remoting.netty

import io.netty.util.internal.logging.InternalLogLevel
import io.netty.util.internal.logging.InternalLogger
import io.netty.util.internal.logging.InternalLoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NettyLogger(TODO 应该移动到logging)
 */
object NettyLogger {
    private val nettyLoggerSeted = AtomicBoolean(false)
    private val nettyLogLevel = InternalLogLevel.ERROR

    fun initNettyLogger() {
        if (!nettyLoggerSeted.get()) {
            try {
                InternalLoggerFactory.setDefaultFactory(NettyBridgeLoggerFactory())
            } catch (e: Throwable) {
                //ignore
            }
            nettyLoggerSeted.set(true)
        }
    }

    private class NettyBridgeLoggerFactory : InternalLoggerFactory() {
        override fun newInstance(s: String): InternalLogger {
            return NettyBridgeLogger(s)
        }
    }

    private class NettyBridgeLogger(name: String) : InternalLogger {
        private var logger: com.agmtopy.kocketmq.logging.InternalLogger

        init {
            logger = com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory.getLogger(name)
        }

        override fun name(): String {
            return logger.getName()
        }

        override fun isEnabled(internalLogLevel: InternalLogLevel): Boolean {
            return nettyLogLevel.ordinal <= internalLogLevel.ordinal
        }

        override fun log(internalLogLevel: InternalLogLevel, s: String) {
            if (internalLogLevel == InternalLogLevel.DEBUG) {
                logger.debug(s)
            }
            if (internalLogLevel == InternalLogLevel.TRACE) {
                logger.info(s)
            }
            if (internalLogLevel == InternalLogLevel.INFO) {
                logger.info(s)
            }
            if (internalLogLevel == InternalLogLevel.WARN) {
                logger.warn(s)
            }
            if (internalLogLevel == InternalLogLevel.ERROR) {
                logger.error(s)
            }
        }

        override fun log(internalLogLevel: InternalLogLevel, s: String, o: Any) {
            if (internalLogLevel == InternalLogLevel.DEBUG) {
                logger.debug(s, o)
            }
            if (internalLogLevel == InternalLogLevel.TRACE) {
                logger.info(s, o)
            }
            if (internalLogLevel == InternalLogLevel.INFO) {
                logger.info(s, o)
            }
            if (internalLogLevel == InternalLogLevel.WARN) {
                logger.warn(s, o)
            }
            if (internalLogLevel == InternalLogLevel.ERROR) {
                logger.error(s, o)
            }
        }

        override fun log(internalLogLevel: InternalLogLevel, s: String, o: Any, o1: Any) {
            if (internalLogLevel == InternalLogLevel.DEBUG) {
                logger.debug(s, o, o1)
            }
            if (internalLogLevel == InternalLogLevel.TRACE) {
                logger.info(s, o, o1)
            }
            if (internalLogLevel == InternalLogLevel.INFO) {
                logger.info(s, o, o1)
            }
            if (internalLogLevel == InternalLogLevel.WARN) {
                logger.warn(s, o, o1)
            }
            if (internalLogLevel == InternalLogLevel.ERROR) {
                logger.error(s, o, o1)
            }
        }

        override fun log(internalLogLevel: InternalLogLevel, s: String, vararg objects: Any) {
            if (internalLogLevel == InternalLogLevel.DEBUG) {
                logger.debug(s, objects)
            }
            if (internalLogLevel == InternalLogLevel.TRACE) {
                logger.info(s, objects)
            }
            if (internalLogLevel == InternalLogLevel.INFO) {
                logger.info(s, objects)
            }
            if (internalLogLevel == InternalLogLevel.WARN) {
                logger.warn(s, objects)
            }
            if (internalLogLevel == InternalLogLevel.ERROR) {
                logger.error(s, objects)
            }
        }

        override fun log(internalLogLevel: InternalLogLevel, s: String, throwable: Throwable) {
            if (internalLogLevel == InternalLogLevel.DEBUG) {
                logger.debug(s, throwable)
            }
            if (internalLogLevel == InternalLogLevel.TRACE) {
                logger.info(s, throwable)
            }
            if (internalLogLevel == InternalLogLevel.INFO) {
                logger.info(s, throwable)
            }
            if (internalLogLevel == InternalLogLevel.WARN) {
                logger.warn(s, throwable)
            }
            if (internalLogLevel == InternalLogLevel.ERROR) {
                logger.error(s, throwable)
            }
        }

        override fun isTraceEnabled(): Boolean {
            return isEnabled(InternalLogLevel.TRACE)
        }

        override fun trace(var1: String) {
            logger.info(var1)
        }

        override fun trace(var1: String, var2: Any) {
            logger.info(var1, var2)
        }

        override fun trace(var1: String, var2: Any, var3: Any) {
            logger.info(var1, var2, var3)
        }

        override fun trace(var1: String, vararg var2: Any) {
            logger.info(var1, var2)
        }

        override fun trace(var1: String, var2: Throwable) {
            logger.info(var1, var2)
        }

        override fun isDebugEnabled(): Boolean {
            return isEnabled(InternalLogLevel.DEBUG)
        }

        override fun debug(var1: String) {
            logger.debug(var1)
        }

        override fun debug(var1: String, var2: Any) {
            logger.debug(var1, var2)
        }

        override fun debug(var1: String, var2: Any, var3: Any) {
            logger.debug(var1, var2, var3)
        }

        override fun debug(var1: String, vararg var2: Any) {
            logger.debug(var1, var2)
        }

        override fun debug(var1: String, var2: Throwable) {
            logger.debug(var1, var2)
        }

        override fun isInfoEnabled(): Boolean {
            return isEnabled(InternalLogLevel.INFO)
        }

        override fun info(var1: String) {
            logger.info(var1)
        }

        override fun info(var1: String, var2: Any) {
            logger.info(var1, var2)
        }

        override fun info(var1: String, var2: Any, var3: Any) {
            logger.info(var1, var2, var3)
        }

        override fun info(var1: String, vararg var2: Any) {
            logger.info(var1, var2)
        }

        override fun info(var1: String, var2: Throwable) {
            logger.info(var1, var2)
        }

        override fun isWarnEnabled(): Boolean {
            return isEnabled(InternalLogLevel.WARN)
        }

        override fun warn(var1: String) {
            logger.warn(var1)
        }

        override fun warn(var1: String, var2: Any) {
            logger.warn(var1, var2)
        }

        override fun warn(var1: String, vararg var2: Any) {
            logger.warn(var1, var2)
        }

        override fun warn(var1: String, var2: Any, var3: Any) {
            logger.warn(var1, var2, var3)
        }

        override fun warn(var1: String, var2: Throwable) {
            logger.warn(var1, var2)
        }

        override fun isErrorEnabled(): Boolean {
            return isEnabled(InternalLogLevel.ERROR)
        }

        override fun error(var1: String) {
            logger.error(var1)
        }

        override fun error(var1: String, var2: Any) {
            logger.error(var1, var2)
        }

        override fun error(var1: String, var2: Any, var3: Any) {
            logger.error(var1, var2, var3)
        }

        override fun error(var1: String, vararg var2: Any) {
            logger.error(var1, var2)
        }

        override fun error(var1: String, var2: Throwable) {
            logger.error(var1, var2)
        }
    }
}