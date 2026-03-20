# KocketMQ

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**KocketMQ** 是 Apache RocketMQ 的 Kotlin 重写版本，旨在提供更简洁的代码实现和更高效的并发性能。

## 核心特性

- **Kotlin 协程优化** - 使用协程替代传统线程池，简化并发编程模型
- **Actor 模型架构** - 无锁并发设计，通过 Channel 实现消息驱动
- **代码简洁** - 消除 Java 冗余语法，提升可维护性
- **协议兼容** - 保持与 RocketMQ 协议兼容，现有客户端可直接连接
- **高性能存储** - 基于 MappedFile 的 CommitLog/ConsumeQueue 存储引擎
- **模块化设计** - 清晰的模块边界，易于扩展和测试

## 模块架构

| 模块 | 包名 | 职责 | 状态 |
|------|------|------|------|
| **logging** | `com.agmtopy.kocketmq.logging` | 日志抽象层 | ✅ 已完成 |
| **common** | `com.agmtopy.kocketmq.common` | 公共数据结构、协议、工具类 | ✅ 已完成 |
| **remoting** | `com.agmtopy.kocketmq.remoting` | 基于 Netty 的网络通信层 | ✅ 已完成 |
| **namesrv** | `com.agmtopy.kocketmq.logging` | NameServer（路由管理、配置管理） | ✅ 已完成 |
| **broker** | `com.agmtopy.kocketmq.broker` | Broker（消息存储、请求处理） | 🚧 开发中 |
| **client** | `com.agmtopy.kocketmq.client` | Producer/Consumer 客户端 | 📅 计划中 |

**模块依赖关系：**
```
client → common → logging
       ↘ remoting ↗

broker → common → logging
      ↘ remoting ↗
      ↘ store
```

## 快速开始

### 环境要求

- JDK 11+
- Gradle 7.0.2+
- Kotlin 1.5.31+

### 构建项目

```bash
# 构建所有模块
./gradlew build

# 运行测试
./gradlew test

# 运行单个测试类
./gradlew test --tests "com.agmtopy.kocketmq.SomeTest"
```

### 启动 NameServer

```bash
# 方式1：使用 Gradle
./gradlew :namesrv:run

# 方式2：直接运行主类
./gradlew :namesrv:build
java -cp namesrv/build/libs/*:common/build/libs/*:remoting/build/libs/*:logging/build/libs/* \
  com.agmtopy.kocketmq.logging.NamesrvStartup
```

### 创建 Topic（待 Broker 实现后）

```bash
# 使用命令行工具创建 Topic
./gradlew :client:run --args="createTopic -t TestTopic -n 4"
```

## 技术栈

### 核心技术

- **Kotlin 1.5.31** - 主要开发语言
- **Kotlin Coroutines 1.5.2** - 异步并发框架
- **Netty 4.0.42.Final** - 网络通信框架
- **JUnit Jupiter 5.5.2** - 测试框架

### 核心设计理念

**Actor 模型 + 消息驱动 + 无锁并发**

整个系统由多个独立的 Actor 组成，每个 Actor：
- 拥有独立的协程作用域（CoroutineScope）
- 通过 Channel 接收消息请求
- 内部串行处理，无需锁
- 通过 Channel/Flow 向外发送事件

**优势：**
1. 充分发挥协程优势，代码简洁高效
2. 无锁设计，并发性能更好
3. 更符合 Kotlin 惯用风格
4. 易于测试和维护

## 性能目标

| 指标 | 目标值 |
|------|--------|
| 单机 TPS | > 10万/秒 |
| 发送延迟 P99 | < 10ms |
| 拉取延迟 P99 | < 5ms |
| 消息可靠性 | 99.99% |

## 文档

- [Broker 核心设计文档](docs/superpowers/specs/2026-03-20-broker-core-design.md)
- [快速开始指南](docs/superpowers/quickstart.md)
- [项目开发指引](CLAUDE.md)

## 开发路线

### 阶段 1：基础存储（2周）✅ 设计完成
- 实现 MappedFile（文件内存映射）
- 实现 CommitLogActor（消息追加）
- 实现 ConsumeQueueBuilderActor（索引构建）
- 实现 MessageStoreActor（协调层）

### 阶段 2：Broker 核心（3周）🚧 进行中
- 实现 BrokerController（启动/关闭流程）
- 实现请求分发和处理器
- 实现 Topic 和消费者管理
- 向 NameServer 注册

### 阶段 3：Producer 客户端（2周）📅 计划中
- 实现消息发送逻辑
- 路由缓存和重试机制
- 批量发送优化

### 阶段 4：Consumer 客户端（3周）📅 计划中
- 实现消息拉取逻辑
- Rebalance 负载均衡
- Offset 管理

### 阶段 5：测试和优化（2周）📅 计划中
- 端到端集成测试
- 性能测试和优化
- 文档完善

## 贡献指南

欢迎贡献代码、报告问题或提出建议。请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

本项目采用 Apache License 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件。

**重要声明：**

KocketMQ 是基于 [Apache RocketMQ](https://github.com/apache/rocketmq) 的衍生项目，遵循 Apache License 2.0 协议。

- 原项目版权：Copyright 2016-2026 The Apache Software Foundation
- 本项目版权：Copyright 2026 The KocketMQ Authors
- 许可证类型：Apache License 2.0

**主要修改：**
- 使用 Kotlin 重写原 Java 代码
- 用 Kotlin 协程替代线程模型
- 移除 TLS 安全功能（计划以插件形式支持）
- 采用 Actor 模型重构并发设计
- 简化代码结构，移除冗余代码

根据 Apache License 2.0 要求：
- ✅ 保留了原始版权声明
- ✅ 标注了修改内容
- ✅ 使用相同的开源协议
- ✅ 包含原始项目的 NOTICE 信息（如有）

## 致谢

特别感谢以下项目和社区：

- **Apache RocketMQ** - 本项目基于 RocketMQ 进行重写，感谢 RocketMQ 社区的杰出贡献
- **Kotlin Team** - 提供了优秀的 Kotlin 语言和协程框架
- **Netty Project** - 提供高性能的网络通信框架

## 免责声明

本项目按"原样"提供，不提供任何明示或暗示的保证。使用本项目的风险由用户自行承担。详见许可证第 7-8 条款。
