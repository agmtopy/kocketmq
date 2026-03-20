# 阶段2：Broker服务器基础设施 - 设计文档

## 概述

阶段2将构建Broker的服务器层，集成Netty网络层、请求处理器和配置管理，实现一个完整可运行的Broker服务。

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                     BrokerStartup                            │
│                    (启动入口)                                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   BrokerController                           │
│              (主控制器，管理所有组件)                         │
└──────────┬──────────────┬──────────────┬────────────────────┘
           │              │              │
    ┌──────▼──────┐  ┌───▼──────┐  ┌───▼────────────┐
    │MessageStore │  │Config    │  │RemotingServer  │
    │  Actor      │  │Managers  │  │  (Netty)       │
    └─────────────┘  └───┬──────┘  └───┬────────────┘
                          │              │
                  ┌───────▼─────┐   ┌───▼────────────┐
                  │TopicConfig  │   │RequestProcessors│
                  │Manager      │   │- SendMessage   │
                  │ConsumerOff- │   │- PullMessage   │
                  │setManager   │   │- AdminCommands │
                  └─────────────┘   └────────────────┘
```

## 核心组件

### 2.1 BrokerController（主控制器）

**职责：**
- 管理Broker的所有组件生命周期
- 初始化和启动各子系统
- 协调组件间的交互
- 提供关闭钩子

**核心接口：**
```kotlin
class BrokerController(val brokerConfig: BrokerConfig) {
    val messageStore: MessageStoreActor
    val topicConfigManager: TopicConfigManager
    val consumerOffsetManager: ConsumerOffsetManager
    val remotingServer: NettyRemotingServer

    suspend fun initialize(): Boolean
    fun start()
    suspend fun shutdown()
}
```

### 2.2 请求处理器

#### SendMessageProcessor
**请求码：** SEND_MESSAGE (10), SEND_MESSAGE_V2 (310), SEND_BATCH_MESSAGE (320)

**功能：**
- 接收生产者发送的消息
- 调用MessageStoreActor存储消息
- 返回发送结果（offset、queueId等）

**流程：**
```
Client -> RemotingServer -> SendMessageProcessor
       -> MessageStoreActor.putMessage()
       -> 返回SendResult
```

#### PullMessageProcessor
**请求码：** PULL_MESSAGE (11)

**功能：**
- 处理消费者拉取消息请求
- 根据topic、queueId、offset查询消息
- 支持最大拉取数量限制

**流程：**
```
Client -> RemotingServer -> PullMessageProcessor
       -> MessageStoreActor.getMessages()
       -> 返回PullResult
```

#### AdminCommandProcessor（管理命令）
**请求码：** UPDATE_AND_CREATE_TOPIC (17), GET_ALL_TOPIC_CONFIG (21)等

**功能：**
- 创建/更新Topic配置
- 查询Topic配置
- 查询Broker运行信息

### 2.3 配置管理

#### TopicConfigManager
**职责：**
- 管理Topic配置（queue数量、权限等）
- 支持动态创建Topic
- 持久化Topic配置到磁盘

**数据结构：**
```kotlin
data class TopicConfig(
    val topicName: String,
    val readQueueNums: Int,
    val writeQueueNums: Int,
    val perm: Int,  // 权限：读写
    val topicFilterType: TopicFilterType
)
```

#### ConsumerOffsetManager
**职责：**
- 管理消费者组的消费进度
- 持久化offset到磁盘
- 支持offset查询和更新

**数据结构：**
```kotlin
// key: topic@consumerGroup, value: Map<queueId, offset>
val offsetTable: MutableMap<String, MutableMap<Int, Long>>
```

### 2.4 协议与编解码

**使用现有的RemotingCommand：**
- 已有完整的协议封装
- 支持JSON和RocketMQ二进制序列化
- 包含请求头、请求体、扩展字段

**请求头示例：**
```kotlin
class SendMessageRequestHeader(
    var topic: String,
    var queueId: Int,
    var sysFlag: Int,
    var bornTimestamp: Long,
    var flag: Int,
    var properties: String?,
    var reconsumeTimes: Int,
    var unitMode: Boolean
) : CommandCustomHeader
```

## 实施计划

### 里程碑2.1：BrokerController基础框架
- [ ] BrokerController核心类
- [ ] BrokerStartup启动类
- [ ] 集成MessageStoreActor
- [ ] 集成NettyRemotingServer

**预估时间：** 0.5天
**代码量：** ~300行

### 里程碑2.2：配置管理器
- [ ] TopicConfigManager
- [ ] ConsumerOffsetManager
- [ ] 配置持久化（JSON格式）
- [ ] 配置加载和恢复

**预估时间：** 0.5天
**代码量：** ~400行

### 里程碑2.3：消息处理器
- [ ] SendMessageProcessor + 请求头
- [ ] PullMessageProcessor + 请求头
- [ ] 响应头和响应体
- [ ] 单元测试

**预估时间：** 1天
**代码量：** ~600行

### 里程碑2.4：集成测试
- [ ] 启动Broker服务器
- [ ] 客户端发送消息
- [ ] 客户端拉取消息
- [ ] 性能测试

**预估时间：** 0.5天
**代码量：** ~400行

## 技术要点

### 1. Actor与Netty的集成
**挑战：** Netty的线程模型 vs Actor模型

**解决方案：**
- Netty线程负责网络I/O
- 请求处理器通过Channel将请求转发给Actor
- Actor串行处理，避免锁竞争

```kotlin
class SendMessageProcessor(
    private val messageStore: MessageStoreActor
) : NettyRequestProcessor {

    override fun processRequest(
        ctx: ChannelHandlerContext,
        request: RemotingCommand
    ): RemotingCommand = runBlocking {
        // 调用Actor存储消息
        val result = messageStore.putMessage(message)
        // 构建响应
        buildResponse(result)
    }
}
```

### 2. 配置持久化
**策略：**
- 使用JSON格式保存配置
- 原子写入（先写临时文件，再重命名）
- 启动时从磁盘加载配置
- 定时刷盘（每5秒）

### 3. 动态Topic创建
**流程：**
1. 收到消息时检查Topic是否存在
2. 若不存在且autoCreateTopicEnable=true
3. 使用defaultTopicQueueNums创建Topic
4. 持久化配置

### 4. 并发安全
**Actor模型优势：**
- MessageStoreActor：单协程串行写，无需锁
- TopicConfigManager：使用读写锁保护
- ConsumerOffsetManager：使用并发集合

## 依赖关系

```
broker模块依赖：
- common模块：RequestCode、TopicConfig、协议结构
- remoting模块：NettyRemotingServer、RemotingCommand
- logging模块：日志接口
```

## 测试策略

### 单元测试
- 每个Manager的独立测试
- 每个Processor的独立测试
- 配置序列化/反序列化测试

### 集成测试
- 启动完整Broker
- 发送消息 -> 存储成功
- 拉取消息 -> 数据正确
- 重启Broker -> 配置恢复

### 性能测试
- 单线程发送吞吐量
- 多线程并发发送
- 拉取消息延迟

## 验收标准

1. ✅ Broker能够成功启动并监听端口
2. ✅ 客户端能够连接并发送消息
3. ✅ 消息能够正确存储到CommitLog和ConsumeQueue
4. ✅ 客户端能够拉取到正确的消息
5. ✅ Topic配置能够动态创建和持久化
6. ✅ 消费进度能够正确记录
7. ✅ Broker重启后配置能够恢复
8. ✅ 性能达到预期（>5000 msg/sec）

## 下一步

开始实施里程碑2.1：BrokerController基础框架
