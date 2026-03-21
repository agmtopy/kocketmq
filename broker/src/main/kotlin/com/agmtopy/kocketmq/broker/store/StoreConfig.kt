package com.agmtopy.kocketmq.broker.store

/**
 * 存储配置
 */
data class StoreConfig(
    val commitLogFileSize: Int = 1024 * 1024 * 1024,  // 1GB
    val mappedFileSizeConsumeQueue: Int = 1024 * 1024 * 6,  // 6MB
    val flushIntervalCommits: Int = 1000,  // 刷盘间隔（提交次数）
    val flushIntervalConsumeQueue: Int = 1000  // ConsumeQueue刷盘间隔
)
