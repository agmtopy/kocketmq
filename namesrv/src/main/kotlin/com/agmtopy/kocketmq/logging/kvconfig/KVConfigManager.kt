package com.agmtopy.kocketmq.logging.kvconfig

import com.agmtopy.kocketmq.common.MixAll
import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.common.protocol.body.KVTable
import com.agmtopy.kocketmq.logging.NamesrvController
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * kv配置管理器
 */
class KVConfigManager internal constructor(var namesrvController: NamesrvController) {
    //静态属性
    companion object {
        val log = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME)
    }

    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private val configTable = HashMap<String?, HashMap<String?, String?>?>()

    //加载KVConfigManager
    fun load() {
        var content: String?
        try {
            content = MixAll.file2String(this.namesrvController.namesrvConfig.kvConfigPath)
            //配置文件有值时
            if (Objects.nonNull(content)) {
                //1. 根据配置文件转换为KVConfigSerializeWrapper(调用父类的fromJson需要用@父类.method的写法略微麻烦,但是含义比较清晰)
                val configWrapper = KVConfigSerializeWrapper@ RemotingSerializable.fromJson(
                    content,
                    KVConfigSerializeWrapper::class.java
                )
                //2. 保存配置文件
                if (Objects.nonNull(configWrapper)) {
                    configWrapper.getConfigTable()?.let { this.configTable.putAll(it) }
                    log.info("load KV config table OK")
                }
            }
        } catch (e: IOException) {
            log.warn("Load KV config table exception", e)
        }
    }

    fun putKVConfig(namespace: String?, key: String?, value: String?) {
        try {
            this.lock.writeLock().lockInterruptibly()
            try {
                var kvTable: HashMap<String?, String?>? = configTable[namespace]
                if (null == kvTable) {
                    kvTable = HashMap()
                    configTable[namespace] = kvTable
                    log.info("putKVConfig create new Namespace {}", namespace)
                }
                val prev = kvTable.put(key, value)
                if (null != prev) {
                    log.info(
                        "putKVConfig update config item, Namespace: $namespace Key: $key Value: $value"
                    )
                } else {
                    log.info(
                        "putKVConfig create new config item, Namespace: $namespace Key: $key Value: $value"
                    )
                }
            } finally {
                this.lock.writeLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("putKVConfig InterruptedException", e)
        }
        persist()
    }

    fun persist() {
        try {
            this.lock.readLock().lockInterruptibly()
            try {
                val kvConfigSerializeWrapper = KVConfigSerializeWrapper()
                kvConfigSerializeWrapper.setConfigTable(configTable)
                val content = kvConfigSerializeWrapper.toJson()
                if (null != content) {
                    MixAll.string2File(content, namesrvController.namesrvConfig.kvConfigPath!!)
                }
            } catch (e: IOException) {
                log.error(
                    "persist kvconfig Exception, "
                            + namesrvController.namesrvConfig.kvConfigPath, e
                )
            } finally {
                this.lock.readLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("persist InterruptedException", e)
        }
    }

    fun deleteKVConfig(namespace: String?, key: String?) {
        try {
            this.lock.writeLock().lockInterruptibly()
            try {
                val kvTable: HashMap<String?, String?>? = configTable[namespace]
                if (null != kvTable) {
                    val value = kvTable.remove(key)
                    log.info(
                        "deleteKVConfig delete a config item, Namespace: $namespace Key: $key Value: $value"
                    )
                }
            } finally {
                this.lock.writeLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("deleteKVConfig InterruptedException", e)
        }
        persist()
    }

    fun getKVListByNamespace(namespace: String?): ByteArray? {
        try {
            this.lock.readLock().lockInterruptibly()
            try {
                val kvTable: HashMap<String?, String?>? = configTable[namespace]
                if (null != kvTable) {
                    val table = KVTable()
                    table.table = kvTable
                    return table.encode()
                }
            } finally {
                this.lock.readLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("getKVListByNamespace InterruptedException", e)
        }
        return null
    }

    fun getKVConfig(namespace: String?, key: String?): String? {
        try {
            this.lock.readLock().lockInterruptibly()
            try {
                val kvTable: HashMap<String?, String?>? = configTable[namespace]
                if (null != kvTable) {
                    return kvTable[key]
                }
            } finally {
                this.lock.readLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("getKVConfig InterruptedException", e)
        }
        return null
    }

    fun printAllPeriodically() {
        try {
            this.lock.readLock().lockInterruptibly()
            try {
                log.info("--------------------------------------------------------")
                run {
                    log.info("configTable SIZE: {}", this.configTable.size)
                    val it: MutableIterator<MutableMap.MutableEntry<String?, HashMap<String?, String?>?>> =
                        this.configTable.entries.iterator()
                    while (it.hasNext()) {
                        val (key, value) = it.next()
                        val itSub: Iterator<Map.Entry<String?, String?>> =
                            value!!.entries.iterator()
                        while (itSub.hasNext()) {
                            val (key1, value1) = itSub.next()
                            log.info("configTable NS: $key Key: $key1 Value: $value1")
                        }
                    }
                }
            } finally {
                this.lock.readLock().unlock()
            }
        } catch (e: InterruptedException) {
            log.error("printAllPeriodically InterruptedException", e)
        }
    }

}

private fun <K, V> HashMap<K, V>.putAll(from: Map<String?, Map<String?, String?>?>) {

}
