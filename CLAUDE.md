# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作提供指引。

## 项目概述

KocketMQ 是 Apache RocketMQ 的 Kotlin 重写版本。使用 Kotlin 重写 RocketMQ 核心组件，利用协程优化并发操作，并移除了 TLS 安全功能（后续将以插件形式支持）。

**当前状态**：
- ✅ NameServer 模块已完成
- ✅ Broker 模块已完成（存储引擎、网络层、处理器、性能优化）

## 构建命令

```bash
# 构建所有模块
./gradlew build

# 运行测试 (JUnit Jupiter 5.5.2)
./gradlew test

# 运行单个测试类
./gradlew test --tests "com.agmtopy.kocketmq.broker.SomeTest"

# 运行性能基准测试
./gradlew :broker:test --tests "com.agmtopy.kocketmq.broker.perf.*"

# 启动 NameServer (入口: NamesrvStartup)
./gradlew :namesrv:run   # 或直接运行 NamesrvStartup.main()

# 启动 Broker (入口: BrokerStartup)
# 直接运行 BrokerStartup.main()，需配置环境变量或修改配置文件
```

构建工具：Gradle 8.7，Kotlin 1.5.31，Java 21 源码兼容。

## 模块架构

`settings.gradle` 中定义了五个模块：

| 模块 | 包名 | 职责 |
|------|------|------|
| **logging** | `com.agmtopy.kocketmq.logging` | 日志抽象层 (`InternalLogger`, `InternalLoggerFactory`) |
| **common** | `com.agmtopy.kocketmq.common` | 公共数据结构、工具类、协议头/体、常量 |
| **remoting** | `com.agmtopy.kocketmq.remoting` | 基于 Netty 的网络通信层 (`RemotingServer`, `RemotingCommand`, 编解码) |
| **namesrv** | `com.agmtopy.kocketmq.logging` | NameServer 实现（Broker 路由管理、配置管理、请求处理） |
| **broker** | `com.agmtopy.kocketmq.broker` | Broker 实现（消息存储、消息处理、配置管理、统计监控） |

依赖链：
- `namesrv` → `common` → `logging`
- `namesrv` → `remoting` → `logging`
- `broker` → `common` → `logging`
- `broker` → `remoting` → `logging`

## 核心架构说明

### NameServer

入口为 `NamesrvStartup`，控制器为 `NamesrvController`。`RouteInfoManager` 使用 `ReadWriteLock` 管理 topic 到 broker 的路由表并发访问。`DefaultRequestProcessor` 处理 20+ 种请求码。`KVConfigManager` 管理键值配置。`BrokerHousekeepingService` 监控 broker 存活状态。

### Broker

Broker 采用 Actor 模型 + Kotlin 协程实现高并发消息存储和处理。

**核心组件**：
- **BrokerController**：主控制器，管理所有组件生命周期
- **MessageStoreActor**：消息存储引擎（CommitLog + ConsumeQueue）
- **TopicConfigManager**：Topic 配置管理
- **ConsumerOffsetManager**：消费者进度追踪
- **NettyRemotingServer**：网络服务层
- **BrokerStats**：统计信息收集

**消息处理器**：
- **SendMessageProcessor**：消息发送处理
- **PullMessageProcessor**：消息拉取处理
- **AdminBrokerProcessor**：管理命令处理
- **QueryMessageProcessor**：消息查询处理
- **BatchSendMessageProcessor**：批量消息发送

**性能优化**：
- Actor 模型：使用协程 Channel 实现无锁并发
- 内存映射文件：CommitLog 和 ConsumeQueue 均使用 MMAP
- 批量处理：支持批量消息发送和拉取
- 消息压缩：GZIP 压缩支持
- 零拷贝传输：使用 FileRegion 优化网络传输

### Remoting 层

基于 Netty 4.1.100 构建。`RemotingCommand` 是协议消息封装类，负责编解码。支持同步、异步和单向调用。序列化支持 JSON 和 RocketMQ 自定义二进制格式。`@CFNotNull`/`@CFNullable` 注解用于反序列化时的字段校验。

### Common 模块

`MixAll` 提供文件 I/O、网络和属性管理工具。`Configuration` 管理配置持久化（原子写入和备份）。`DataVersion` 跟踪配置变更版本。协议体（`ClusterInfo`、`KVTable`、`RegisterBrokerBody`）和路由结构（`TopicRouteData`、`BrokerData`、`QueueData`）定义在此模块。

## 代码约定

- 主要使用 Kotlin（96%+）；logging 模块中有少量 Java 示例文件
- namesrv 模块的源码包名为 `com.agmtopy.kocketmq.logging`（与原始项目的组织方式一致）
- broker 模块使用 Actor 模型 + Kotlin 协程，避免显式锁使用
- 所有测试使用 JUnit Jupiter 5.5.2，支持协程测试

## 开发进度

### 已完成功能 ✅

**Phase 1: 核心存储引擎**
- MappedFile 内存映射文件层
- MessageExt 消息编码/解码
- CommitLogActor 顺序消息存储
- ConsumeQueueBuilderActor 索引构建
- MessageStoreActor 统一存储接口

**Phase 2: Broker 服务器基础设施**
- BrokerController 生命周期管理
- TopicConfigManager Topic 配置管理
- ConsumerOffsetManager 消费者进度追踪
- SendMessageProcessor 消息发送处理
- PullMessageProcessor 消息拉取处理
- NettyRemotingServer 集成

**Phase 3: 高级功能**
- AdminBrokerProcessor 管理命令
- QueryMessageProcessor 消息查询
- BrokerStats 统计信息

**Phase 4: 性能优化**
- BatchSendMessageProcessor 批量发送
- MessageCompressor 消息压缩
- StoreOptimizer 存储优化

**统计**：
- 总代码量：~5,526 行
- 测试用例：111 个
- 性能：发送 TPS > 5000，拉取 TPS > 3000

### 待开发功能 📋

- 消费者组管理
- 事务消息
- 延迟消息
- HA 高可用
- 消息轨迹追踪
- 性能监控面板

## 用户偏好

根据项目开发过程中的记录：

- **提交策略**：每个里程碑完成后提交
- **构建策略**：先写完所有代码，项目完成后再运行 gradle 操作（gradle 耗时，推迟编译/测试）
- **文档策略**：使用 memory 文件记录进度
- **提交消息**：从 Phase 3+ 开始使用中文提交消息

## 参考资料

- Apache RocketMQ 官方文档：http://rocketmq.apache.org/
- RocketMQ 设计文档：https://github.com/apache/rocketmq/tree/master/docs/cn
- Kotlin 协程文档：https://kotlinlang.org/docs/coroutines-overview.html
