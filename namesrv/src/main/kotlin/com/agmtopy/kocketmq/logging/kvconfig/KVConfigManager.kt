package com.agmtopy.kocketmq.logging.kvconfig

import com.agmtopy.kocketmq.common.MixAll
import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.logging.NamesrvController
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap

/**
 * kv配置管理器
 */
class KVConfigManager internal constructor(var namesrvController: NamesrvController) {
    //静态属性
    companion object {
        val log = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME)
    }

    private val configTable = HashMap<String?, Map<String?, String?>?>()

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

}