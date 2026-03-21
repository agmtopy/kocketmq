package com.agmtopy.kocketmq.logging.inner

import com.agmtopy.kocketmq.logging.InternalLogger

/**
 * ConsoleLogger工厂实现
 * 用于创建ConsoleLogger实例
 */
class ConsoleLoggerFactory : InternalLoggerFactory() {

    companion object {
        /**
         * 注册ConsoleLogger工厂
         */
        fun register() {
            val factory = ConsoleLoggerFactory()
            loggerFactoryCache[LOGGER_INNER] = factory
            loggerType = LOGGER_INNER
        }
    }

    override fun getLoggerInstance(name: String): InternalLogger {
        return ConsoleLogger(name)
    }

    override fun getLoggerType(): String {
        return LOGGER_INNER
    }

    override fun shutdown() {
        // Console logger不需要关闭资源
    }
}
