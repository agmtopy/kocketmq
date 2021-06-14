package com.agmtopy.kocketmq.logging

import com.agmtopy.kocketmq.common.Configuration
import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.common.namesrv.NamesrvConfig
import com.agmtopy.kocketmq.common.util.FileWatchService
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.logging.kvconfig.KVConfigManager
import com.agmtopy.kocketmq.logging.routeinfo.BrokerHousekeepingService
import com.agmtopy.kocketmq.logging.routeinfo.RouteInfoManager
import com.agmtopy.kocketmq.remoting.RemotingServer
import com.agmtopy.kocketmq.remoting.netty.NettyServerConfig
import java.util.concurrent.ExecutorService

/**
 * namesrcController执行NameServer的逻辑
 */
class NamesrvController(var namesrvConfig: NamesrvConfig, var nettyServerConfig: NettyServerConfig) {
    companion object {
        val log: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME)
    }

    private val kvConfigManager: KVConfigManager
    private val routeInfoManager: RouteInfoManager
    private val brokerHousekeepingService: BrokerHousekeepingService
    private val configuration: Configuration

    //延迟初始化熟悉啊
    private lateinit var remotingServer: RemotingServer
    private lateinit var remotingExecutor: ExecutorService
    private lateinit var fileWatchService: FileWatchService

    //构造函数
    init {
        kvConfigManager = KVConfigManager(this)
        routeInfoManager = RouteInfoManager()
        brokerHousekeepingService = BrokerHousekeepingService()
        this.configuration = Configuration(log, arrayOf(namesrvConfig, nettyServerConfig))
        this.configuration.setStorePathFromConfig(this.namesrvConfig, "configStorePath")
    }

    //初始化
    fun initialize(): Boolean {
        //1. 加载配置文件
        this.kvConfigManager.load()
        //2. 初始化remotingServer
        this.remotingServer = NettyRemotingServer


        return false
    }


}