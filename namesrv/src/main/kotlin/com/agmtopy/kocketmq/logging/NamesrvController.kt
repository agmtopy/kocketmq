package com.agmtopy.kocketmq.logging

import com.agmtopy.kocketmq.common.Configuration
import com.agmtopy.kocketmq.common.concurrent.ThreadFactoryImpl
import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.common.namesrv.NamesrvConfig
import com.agmtopy.kocketmq.common.util.FileWatchService
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.logging.kvconfig.KVConfigManager
import com.agmtopy.kocketmq.logging.routeinfo.BrokerHousekeepingService
import com.agmtopy.kocketmq.logging.routeinfo.RouteInfoManager
import com.agmtopy.kocketmq.remoting.RemotingServer
import com.agmtopy.kocketmq.remoting.netty.NettyRemotingServer
import com.agmtopy.kocketmq.remoting.netty.NettyServerConfig
import java.util.concurrent.*

/**
 * namesrcController执行NameServer的逻辑
 */
class NamesrvController(var namesrvConfig: NamesrvConfig, var nettyServerConfig: NettyServerConfig) {
    companion object {
        val log: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME)
    }

    val kvConfigManager: KVConfigManager
    val routeInfoManager: RouteInfoManager
    private val brokerHousekeepingService: BrokerHousekeepingService
    val configuration: Configuration

    //延迟初始化-lateinit
    private lateinit var remotingServer: RemotingServer
    private lateinit var remotingExecutor: ExecutorService
    private lateinit var fileWatchService: FileWatchService

    //默认成员变量
    private val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        ThreadFactoryImpl(
            "NSScheduledThread"
        )
    )

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
        this.remotingServer = NettyRemotingServer(this.nettyServerConfig, this.brokerHousekeepingService)
        //3. 创建远程服务线程池(根据config配置文件创建默认的大小的固定线程池)
        val threadPoolExecutor = ThreadPoolExecutor(
            this.nettyServerConfig.getServerWorkerThreads(),
            this.nettyServerConfig.getServerWorkerThreads(),
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(),
            ThreadFactoryImpl("RemotingExecutorThread_")
        )
        //4. 注册Namesrv请求的处理器
        this.registerProcessor()

        //5. 设置定期处理broker的无效任务(@TODO 应抽象为统一的namesrv的定时任务)
        this.scheduledExecutorService.scheduleAtFixedRate(
            Runnable { routeInfoManager.scanNotActiveBroker() },
            5,
            10,
            TimeUnit.SECONDS
        )

        return false
    }

    /**
     * 向RemotingServer注册Namesrv请求处理器,
     */
    private fun registerProcessor() {
        //判断是否为集群测试(集群测试@TODO 先按下不表)
        if (namesrvConfig.isClusterTest()) {
            remotingServer.registerDefaultProcessor(
                ClusterTestRequestProcessor(this, namesrvConfig.getProductEnvName()),
                remotingExecutor
            )
        } else {
            remotingServer.registerDefaultProcessor(DefaultRequestProcessor(this), remotingExecutor)
        }

    }

    /**
     * start
     */
    fun start() {

    }

    /**
     */
    fun shutdown() {

    }
}