package com.agmtopy.kocketmq.logging.inner

import com.agmtopy.kocketmq.logging.InternalLogger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 抽象内部log工厂
 */
abstract class InternalLoggerFactory {

    companion object {

        var LOGGER_SLF4J = "slf4j"
        var LOGGER_INNER = "inner"

        var DEFAULT_LOGGER = LOGGER_SLF4J

        var loggerType:String? = null

        var loggerFactoryCache:ConcurrentHashMap<String, InternalLoggerFactory> = ConcurrentHashMap<String, InternalLoggerFactory>()

        /**
         * 根据ClassType获取InternalLogger
         */
        fun getLogger(clazz: Class<*>): InternalLogger? {
            return getLogger(clazz.name)
        }

        /**
         * 根据ClassName获取InternalLogger
         */
        @JvmStatic
        fun getLogger(className: String): InternalLogger {
            return getLoggerFactory().getLoggerInstance(className);
        }

        /**
         * 获取/创建:InternalLoggerFactory
         * kotlin语法限制返回不能为null
         */
        fun getLoggerFactory(): InternalLoggerFactory {
            //1. loggerType存在时直接返回cache中的对象
            if (Objects.nonNull(loggerType)) {
                return loggerFactoryCache.get(loggerType)!!
            }

            //2.尝试获取默认或者内部的factory对象
            val defaultFactory = loggerFactoryCache.get(DEFAULT_LOGGER)
            if (Objects.nonNull(defaultFactory)) {
                return defaultFactory!!
            }

            var innerFactory = loggerFactoryCache.get(LOGGER_INNER)
            if (Objects.nonNull(innerFactory)) {
                return innerFactory!!
            }

            //3. 无factory时抛出异常
            throw RuntimeException("[RocketMQ] Logger init failed, please check logger")
        }
    }

    /**
     * 根据名称返回内部log实现
     */
    abstract fun getLoggerInstance(name: String): InternalLogger

    /**
     * 注册
     */
    protected fun doRegister(){
        getLoggerType()
    }

    protected abstract fun getLoggerType():String

    protected abstract fun shutdown()
}