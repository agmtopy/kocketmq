package com.agmtopy.kocketmq.namesrv

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory

object TestLoggerBootstrap {
    private object NoopLogger : InternalLogger {
        override fun getName(): String = "noop"
        override fun info(s: String) = Unit
        override fun info(var1: String?, var2: Any?) = Unit
        override fun info(var1: String?, var2: Any?, var3: Any?) = Unit
        override fun warn(s: String) = Unit
        override fun warn(s: String, e: Throwable) = Unit
        override fun warn(var1: String?, var2: Any?) = Unit
        override fun warn(var1: String?, var2: Any?, var3: Any?) = Unit
        override fun debug(s: String) = Unit
        override fun debug(var1: String?, var2: Any?) = Unit
        override fun debug(var1: String?, var2: Any?, var3: Any?) = Unit
        override fun error(s: String) = Unit
        override fun error(var1: String?, var2: Any?) = Unit
        override fun error(var1: String?, var2: Any?, var3: Any?) = Unit
    }

    private class NoopLoggerFactory : InternalLoggerFactory() {
        override fun getLoggerInstance(name: String): InternalLogger = NoopLogger
        override fun getLoggerType(): String = LOGGER_SLF4J
        override fun shutdown() = Unit
    }

    fun ensureRegistered() {
        if (!InternalLoggerFactory.loggerFactoryCache.containsKey(InternalLoggerFactory.DEFAULT_LOGGER)) {
            InternalLoggerFactory.loggerFactoryCache[InternalLoggerFactory.DEFAULT_LOGGER] = NoopLoggerFactory()
        }
    }
}
