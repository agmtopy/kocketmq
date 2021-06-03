package com.agmtopy.kocketmq.logging

import com.agmtopy.kocketmq.common.namesrv.NamesrvConfig
import com.agmtopy.kocketmq.remoting.netty.NettyServerConfig
import java.util.concurrent.ExecutorService




/**
 * namesrcController执行NameServer的逻辑
 */
class NamesrvController(var namesrvConfig: NamesrvConfig, var nettyServerConfig: NettyServerConfig) {
    companion object {

    }

    private var kvConfigManager: KVConfigManager? = null
    private var routeInfoManager: RouteInfoManager? = null
    private var brokerHousekeepingService: BrokerHousekeepingService? = null
    private var configuration: Configuration? = null


    private var remotingExecutor: ExecutorService? = null
    private var fileWatchService: FileWatchService? = null

    fun initialize(): Boolean {
        this.kvConfigManager = KVConfigManager(this)
        this.routeInfoManager = RouteInfoManager()



        return false
    }
}