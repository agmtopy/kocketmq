package com.agmtopy.kocketmq.common.namesrv

import com.agmtopy.kocketmq.common.MixAll
import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import java.io.File

/**
 * NameServer config
 */
class NamesrvConfig {
    private var rocketmqHome =
        System.getProperty(MixAll.ROCKETMQ_HOME_PROPERTY, System.getenv(MixAll.ROCKETMQ_HOME_ENV))
    var kvConfigPath =
        System.getProperty("user.home") + File.separator + "namesrv" + File.separator + "kvConfig.json"
    private var configStorePath =
        System.getProperty("user.home") + File.separator + "namesrv" + File.separator + "namesrv.properties"
    private var productEnvName = "center"
    private var clusterTest = false
    private var orderMessageEnable = false

    fun isOrderMessageEnable(): Boolean {
        return orderMessageEnable
    }

    fun setOrderMessageEnable(orderMessageEnable: Boolean) {
        this.orderMessageEnable = orderMessageEnable
    }

    fun getRocketmqHome(): String? {
        return rocketmqHome
    }

    fun setRocketmqHome(rocketmqHome: String) {
        this.rocketmqHome = rocketmqHome
    }

    fun getKvConfigPath(): String? {
        return kvConfigPath
    }

    fun setKvConfigPath(kvConfigPath: String) {
        this.kvConfigPath = kvConfigPath
    }

    fun getProductEnvName(): String? {
        return productEnvName
    }

    fun setProductEnvName(productEnvName: String) {
        this.productEnvName = productEnvName
    }

    fun isClusterTest(): Boolean {
        return clusterTest
    }

    fun setClusterTest(clusterTest: Boolean) {
        this.clusterTest = clusterTest
    }

    fun getConfigStorePath(): String? {
        return configStorePath
    }

    fun setConfigStorePath(configStorePath: String) {
        this.configStorePath = configStorePath
    }
}