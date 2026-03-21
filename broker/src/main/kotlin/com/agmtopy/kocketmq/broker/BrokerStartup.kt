package com.agmtopy.kocketmq.broker

import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import kotlinx.coroutines.runBlocking

/**
 * Broker启动类
 *
 * 提供Broker的启动入口和关闭钩子
 */
object BrokerStartup {
    private val log: InternalLogger = InternalLoggerFactory.getLogger(BrokerStartup::class.java)

    private var controller: BrokerController? = null

    @JvmStatic
    fun main(args: Array<String>) {
        log.info("========================================")
        log.info("KocketMQ Broker Startup")
        log.info("========================================")

        try {
            // 1. 解析配置
            val brokerConfig = parseConfig(args)
            log.info("Broker config: $brokerConfig")

            // 2. 创建控制器
            controller = BrokerController(brokerConfig)

            // 3. 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(Thread {
                log.info("Shutdown hook triggered")
                runBlocking {
                    controller?.shutdown()
                }
            })

            // 4. 初始化
            val initialized = runBlocking {
                controller!!.initialize()
            }

            if (!initialized) {
                log.error("Broker initialize failed, exiting...")
                System.exit(1)
            }

            // 5. 启动
            controller!!.start()

            log.info("Broker startup success!")

            // 6. 主线程阻塞，等待关闭
            synchronized(this) {
                (this as Object).wait()
            }

        } catch (e: Exception) {
            log.error("Broker startup failed", e)
            System.exit(1)
        }
    }

    /**
     * 解析配置
     *
     * 支持从命令行参数或配置文件加载配置
     */
    private fun parseConfig(args: Array<String>): BrokerConfig {
        // TODO: 支持从配置文件加载
        // TODO: 支持命令行参数覆盖

        // 目前使用默认配置
        return BrokerConfig(
            brokerName = System.getProperty("brokerName", "DefaultBroker"),
            brokerId = System.getProperty("brokerId", "0").toLong(),
            clusterName = System.getProperty("clusterName", BrokerConfig.DEFAULT_CLUSTER_NAME),
            listenPort = System.getProperty("listenPort", "10911").toInt(),
            storePathRootDir = System.getProperty("storePathRootDir", "/tmp/kocketmq/store")
        )
    }
}
