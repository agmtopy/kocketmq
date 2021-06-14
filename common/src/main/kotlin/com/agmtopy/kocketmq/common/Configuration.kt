package com.agmtopy.kocketmq.common

import com.agmtopy.kocketmq.common.namesrv.NamesrvConfig
import com.agmtopy.kocketmq.logging.InternalLogger
import java.lang.RuntimeException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.ArrayList
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
}