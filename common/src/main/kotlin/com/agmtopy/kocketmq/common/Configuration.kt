package com.agmtopy.kocketmq.common

import com.agmtopy.kocketmq.common.namesrv.NamesrvConfig
import com.agmtopy.kocketmq.logging.InternalLogger
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * 定义的config管理器
 */
class Configuration {
    private var log: InternalLogger

    //config对象集合
    private var configObjectList: List<Any> = ArrayList(4)
    private var storePath: String? = null

    //标识store path是否从config对象中获取
    private var storePathFromConfig = false

    //store config OBJ
    private var storePathObject: Any? = null
    private lateinit var storePathField: Field
    private var dataVersion: DataVersion? = DataVersion()
    private val readWriteLock: ReadWriteLock = ReentrantReadWriteLock()

    private val allConfigs = Properties()


    //初始化读写锁
    private val lock = ReentrantReadWriteLock()

    //入参校验configObjects不为空
    constructor(log: InternalLogger, configObjects: Array<Any>) {
        this.log = log

        for (obj in configObjects) {
            //处理配置参数集合
        }
    }

    /**
     * 装载配置
     */
    fun registerConfig(configObject: Any?): Configuration {
        try {
            configObject!!
        } catch (e: InterruptedException) {

        }
        return this
    }

    /**
     * 根据Config设置存储path
     */
    fun setStorePathFromConfig(namesrvConfig: NamesrvConfig, fieldName: String) {
        try {
            //ReadWriteLock使用双重try-catch结构保证unlock
            lock.writeLock().lockInterruptibly()
            try {
                //设置初始参数
                this.storePathFromConfig = true
                this.storePathObject = namesrvConfig

                //校验配置文件的storePath值是否存在
                this.storePathField = namesrvConfig.javaClass.getDeclaredField(fieldName)
                //检查field字段属性是否是静态的
                assert(!Modifier.isStatic(this.storePathField.modifiers))
                this.storePathField.setAccessible(true)
            } catch (e: NoSuchFieldException) {
                throw RuntimeException(e)
            } finally {
                lock.writeLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("setStorePathFromConfig lock error")
        }
    }

    fun getAllConfigsFormatString(): String? {
        try {
            readWriteLock.readLock().lockInterruptibly()
            return try {
                getAllConfigsInternal()
            } finally {
                readWriteLock.readLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("getAllConfigsFormatString lock error")
        }
        return null
    }

    private fun getAllConfigsInternal(): String? {
        val stringBuilder = StringBuilder()

        // reload from config object ?
        for (configObject in configObjectList) {
            val properties: Properties = MixAll.object2Properties(configObject)!!
            merge(properties, this.allConfigs)
            log.warn("getAllConfigsInternal object2Properties is null, {}", configObject.javaClass)
        }
        run { stringBuilder.append(MixAll.properties2String(this.allConfigs)) }
        return stringBuilder.toString()
    }


    private fun merge(from: Properties, to: Properties) {
        for (key in from.keys) {
            val fromObj = from[key]
            val toObj = to[key]
            if (toObj != null && toObj != fromObj) {
                log.info("Replace, key: $key, value: $toObj -> $fromObj")
            }
            to[key] = fromObj
        }
    }

    private fun mergeIfExist(from: Properties, to: Properties) {
        for (key in from.keys) {
            if (!to.containsKey(key)) {
                continue
            }
            val fromObj = from[key]
            val toObj = to[key]
            if (toObj != null && toObj != fromObj) {
                log.info("Replace, key: $key, value: $toObj -> $fromObj")
            }
            to[key] = fromObj
        }
    }

    fun update(properties: Properties?) {
        try {
            readWriteLock.writeLock().lockInterruptibly()
            try {
                // the property must be exist when update
                mergeIfExist(properties!!, allConfigs)
                for (configObject in configObjectList) {
                    // not allConfigs to update...
                    MixAll.properties2Object(properties, configObject)
                }
                dataVersion!!.nextVersion()
            } finally {
                readWriteLock.writeLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("update lock error, {}", properties)
            return
        }
        persist()
    }

    fun persist() {
        try {
            readWriteLock.readLock().lockInterruptibly()
            try {
                val allConfigs = getAllConfigsInternal()
                MixAll.string2File(allConfigs, getStorePath())
            } catch (e: IOException) {
                log.error("persist string2File error, ", e)
            } finally {
                readWriteLock.readLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("persist lock error")
        }
    }

    private fun getStorePath(): String {
        var realStorePath: String? = null
        try {
            readWriteLock.readLock().lockInterruptibly()
            try {
                realStorePath = storePath
                if (storePathFromConfig) {
                    try {
                        realStorePath = storePathField[storePathObject] as String
                    } catch (e: IllegalAccessException) {
                        log.error("getStorePath error, ", e)
                    }
                }
            } finally {
                readWriteLock.readLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("getStorePath lock error")
        }
        return realStorePath!!
    }
}