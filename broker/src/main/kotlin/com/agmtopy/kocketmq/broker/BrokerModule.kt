package com.agmtopy.kocketmq.broker

/**
 * Broker模块标记
 *
 * 这是KocketMQ Broker的核心模块，负责消息存储和请求处理。
 *
 * 主要组件：
 * - BrokerController: 主控制器
 * - MessageStoreActor: 消息存储
 * - RequestDispatcherActor: 请求分发
 * - TopicManagerActor: Topic管理
 * - ConsumerManagerActor: 消费者管理
 */
object BrokerModule {
    const val VERSION = "1.0.0-SNAPSHOT"
    const val NAME = "KocketMQ Broker"
}
