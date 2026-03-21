package com.agmtopy.kocketmq.broker.config

/**
 * Broker配置
 */
data class BrokerConfig(
    val brokerName: String = "DefaultBroker",
    val brokerId: Long = 0,  // 0=Master, 其他=Slave
    val clusterName: String = "DefaultCluster",
    val listenPort: Int = 10911,
    val namesrvAddr: String = "",

    // 存储配置
    val storePathRootDir: String = "/tmp/kocketmq/store",
    val storePathCommitLog: String = "/tmp/kocketmq/store/commitlog",
    val commitLogFileSize: Int = 1024 * 1024 * 1024,  // 1GB
    val mappedFileSizeConsumeQueue: Int = 1024 * 1024 * 6,  // 6MB

    // 处理配置
    val processThreads: Int = 16,
    val maxPendingRequests: Int = 10000,

    // 心跳配置
    val heartbeatIntervalMs: Long = 30_000,

    // 消费者配置
    val consumerExpiredTimeMs: Long = 120_000,

    // Topic配置
    val autoCreateTopicEnable: Boolean = true,
    val defaultTopicQueueNums: Int = 8,

    // 配置文件路径
    val brokerConfigPath: String = "",
    val topicConfigPath: String = "/tmp/kocketmq/store/config/topics.json",
    val consumerOffsetPath: String = "/tmp/kocketmq/store/config/consumerOffset.json"
) {
    companion object {
        const val DEFAULT_CLUSTER_NAME = "DefaultCluster"
    }
}
