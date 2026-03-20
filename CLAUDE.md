# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作提供指引。

## 项目概述

KocketMQ 是 Apache RocketMQ 的 Kotlin 重写版本。使用 Kotlin 重写 RocketMQ 核心组件，利用协程优化并发操作，并移除了 TLS 安全功能（后续将以插件形式支持）。目前仅实现了 NameServer 模块，Broker 模块尚未开发。

## 构建命令

```bash
# 构建所有模块
./gradlew build

# 运行测试 (JUnit Jupiter 5.5.2)
./gradlew test

# 运行单个测试类
./gradlew test --tests "com.agmtopy.kocketmq.SomeTest"

# 启动 NameServer (入口: NamesrvStartup)
./gradlew :namesrv:run   # 或直接运行 NamesrvStartup.main()
```

构建工具：Gradle 7.0.2，Kotlin 1.5.31，Java 11 源码兼容。

## 模块架构

`settings.gradle` 中定义了四个模块：

| 模块 | 包名 | 职责 |
|------|------|------|
| **logging** | `com.agmtopy.kocketmq.logging` | 日志抽象层 (`InternalLogger`, `InternalLoggerFactory`) |
| **common** | `com.agmtopy.kocketmq.common` | 公共数据结构、工具类、协议头/体、常量 |
| **remoting** | `com.agmtopy.kocketmq.remoting` | 基于 Netty 的网络通信层 (`RemotingServer`, `RemotingCommand`, 编解码) |
| **namesrv** | `com.agmtopy.kocketmq.logging` | NameServer 实现（Broker 路由管理、配置管理、请求处理） |

依赖链：`namesrv` → `common` → `logging`；`namesrv` → `remoting` → `logging`。

## 核心架构说明

**Remoting 层** — 基于 Netty 4.0.42 构建。`RemotingCommand` 是协议消息封装类，负责编解码。支持同步、异步和单向调用。序列化支持 JSON 和 RocketMQ 自定义二进制格式。`@CFNotNull`/`@CFNullable` 注解用于反序列化时的字段校验。

**NameServer** — 入口为 `NamesrvStartup`，控制器为 `NamesrvController`。`RouteInfoManager` 使用 `ReadWriteLock` 管理 topic 到 broker 的路由表并发访问。`DefaultRequestProcessor` 处理 20+ 种请求码。`KVConfigManager` 管理键值配置。`BrokerHousekeepingService` 监控 broker 存活状态。

**Common 模块** — `MixAll` 提供文件 I/O、网络和属性管理工具。`Configuration` 管理配置持久化（原子写入和备份）。`DataVersion` 跟踪配置变更版本。协议体（`ClusterInfo`、`KVTable`、`RegisterBrokerBody`）和路由结构（`TopicRouteData`、`BrokerData`、`QueueData`）定义在此模块。

## 代码约定

- 主要使用 Kotlin（96%+）；logging 模块中有少量 Java 示例文件
- namesrv 模块的源码包名为 `com.agmtopy.kocketmq.logging`（与原始项目的组织方式一致）
- `broker` 模块目录已存在，但尚无源码
- `NamesrvController.start()` 和 `shutdown()` 当前为空实现（TODO）
