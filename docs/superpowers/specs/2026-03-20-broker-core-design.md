# KocketMQ Broker核心设计文档

**日期：** 2026-03-20
**版本：** 1.0
**作者：** Claude Code

---

## 目录

1. [概述](#概述)
2. [设计目标](#设计目标)
3. [整体架构](#整体架构)
4. [核心组件设计](#核心组件设计)
5. [存储引擎设计](#存储引擎设计)
6. [请求处理流程](#请求处理流程)
7. [客户端设计](#客户端设计)
8. [测试策略](#测试策略)
9. [实现计划](#实现计划)

---

## 概述

### 项目背景

KocketMQ是Apache RocketMQ的Kotlin重写版本，目标是：
- 使用Kotlin协程优化并发操作
- 消除Java冗余语法
- 移除TLS安全功能（后续插件化）
- 保持与RocketMQ协议兼容

### 当前状态

**已实现模块：**
- ✅ NameServer（名称服务）
- ✅ Common（公共数据结构、协议、工具类）
- ✅ Remoting（基于Netty的网络通信层）
- ✅ Logging（日志抽象层）

**本次设计范围：**
- 🆕 Broker核心（消息服务器）
- 🆕 Producer Client（生产者客户端）
- 🆕 Consumer Client（消费者客户端）
- 🆕 Store（消息存储引擎）

### 设计约束

根据需求讨论，确定以下设计约束：

1. **完全协程化** - 用Kotlin协程替代线程池、锁、回调机制
2. **重写存储引擎** - 用Kotlin重写CommitLog/ConsumeQueue存储引擎
3. **简化版本** - 先实现单Broker（无主从复制），后续再考虑HA
4. **协议兼容** - 保持与RocketMQ协议兼容，现有RocketMQ客户端可直接连接

---

## 设计目标

### 功能目标

1. **消息发送** - Producer能可靠发送消息到Broker
2. **消息消费** - Consumer能拉取消息并消费
3. **消息持久化** - 消息存储到磁盘，重启后可恢复
4. **Topic管理** - 支持创建、删除、查询Topic
5. **消费者管理** - 支持消费者组注册、Offset管理

### 非功能目标

1. **高性能** - 单机TPS > 10万/秒
2. **低延迟** - 发送消息P99 < 10ms
3. **协程化** - 100%使用协程，无阻塞调用
4. **可维护性** - 代码简洁，职责清晰
5. **可测试性** - 支持单元测试、集成测试

---

## 整体架构

### 系统架构图

```
┌──────────────────────────────────────────────────────────┐
│                    Producer Client                        │
│  - ProducerActor: 消息发送、重试、批量聚合                  │
│  - NameServerClient: 路由发现（获取Topic路由）              │
│  - NettyClient: 网络连接池管理                             │
└─────────────────┬────────────────────────────────────────┘
                  │ 1. 获取路由
                  ↓
┌──────────────────────────────────────────────────────────┐
│              NameServer (已实现 ✓)                        │
│  - RouteInfoManager: 路由表管理                           │
│  - KVConfigManager: 配置管理                              │
│  - NettyRemotingServer: 网络服务                          │
└─────────────────┬────────────────────────────────────────┘
                  │ 2. 返回TopicRouteData
                  ↓
┌──────────────────────────────────────────────────────────┐
│                    Broker Cluster                         │
│                                                           │
│  ┌────────────────────────────────────────────────────┐  │
│  │         BrokerController (主Actor)                  │  │
│  │  - 启动/关闭协调                                     │  │
│  │  - 向NameServer注册、心跳                            │  │
│  │  - 状态监控                          │  │
│  └────────────┬───────────────────────────────────────┘  │
│               │ Channel<Event>                            │
│               ↓                                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │         RemotingServer (现有Netty层)                │  │
│  │  - 协议编解码                                       │  │
│  │  - Channel → Request → Actor转换                    │  │
│  └────────────┬───────────────────────────────────────┘  │
│               │ Channel<RemotingRequest>                  │
│               ↓                                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │       RequestDispatcherActor                        │  │
│  │  - 路由请求到Processor                              │  │
│  │  - 限流控制（背压）                                  │  │
│  └──┬──────┬──────┬──────┬──────┬─────────────────────┘  │
│     │      │      │      │      │                         │
│     ↓      ↓      ↓      ↓      ↓                         │
│  ┌─────┐┌─────┐┌─────┐┌─────┐                             │
│  │Admin││Send ││Pull ││Heart│                             │
│  │Proc ││Msg  ││Msg  │beat │                             │
│  │Actor││Actor││Actor│Actor│                             │
│  └─────┘└──┬──┘└──┬──┘└─────┘                             │
│            │      │                                        │
│            ↓      ↓                                        │
│  ┌─────────────────────────────────────────────────────┐ │
│  │       MessageStoreActor (核心存储)                   │ │
│  │  - CommitLog追加（顺序写）                           │ │
│  │  - ConsumeQueue构建（异步）                          │ │
│  │  - IndexFile构建（异步）                             │ │
│  └───────┬──────────────────┬──────────────────────────┘ │
│          │                  │                             │
│          ↓                  ↓                             │
│  ┌───────────────┐  ┌──────────────┐                     │
│  │CommitLogActor │  │ConsumeQueue  │                     │
│  └───────────────┘  │BuilderActor  │                     │
│                     └──────────────┘                     │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │       TopicManagerActor                             │ │
│  │  - Topic元数据管理                                   │ │
│  │  - Queue分配                                        │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │       ConsumerManagerActor                          │ │
│  │  - 消费者组管理                                      │ │
│  │  - Offset管理                                       │ │
│  │  - Rebalance协调                                    │ │
│  └─────────────────────────────────────────────────────┘ │
└───────────────────┬───────────────────────────────────────┘
                   │ 4. PullMessage
                   ↑
┌──────────────────┴───────────────────────────────────────┐
│                    Consumer Client                        │
│  - ConsumerActor: 消息拉取、重平衡、offset提交             │
│  - RebalanceActor: 负载均衡、队列分配                      │
│  - OffsetManagerActor: offset持久化                       │
│  - NameServerClient: 路由发现                             │
│  - NettyClient: 网络连接池管理                             │
└──────────────────────────────────────────────────────────┘
```

### 模块依赖关系

```
┌─────────────────────────────────────┐
│  Client Module (producer/consumer)  │
│  - 依赖: common, remoting           │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│         Broker Module                │
│  - 依赖: common, remoting, store     │
│  - 向NameServer注册                  │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│       NameServer Module (已实现)     │
│  - 依赖: common, remoting           │
└─────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────┐
│   Store Module (Broker子模块)        │
│  - CommitLog, ConsumeQueue          │
│  - IndexFile                        │
└─────────────────────────────────────┘
```

### 核心设计理念

**Actor模型 + 消息驱动 + 无锁并发**

整个系统由多个独立的Actor组成，每个Actor：
- 拥有独立的协程作用域（CoroutineScope）
- 通过Channel接收消息请求
- 内部串行处理，无需锁
- 通过Channel/Flow向外发送事件

**优势：**
1. 充分发挥协程优势，代码简洁高效
2. 无锁设计，并发性能更好
3. 更符合Kotlin惯用风格
4. 易于测试和维护

---

## 核心组件设计

### 1. BrokerController - 主控Actor

**职责：**
- 协调所有子Actor的启动和关闭
- 向NameServer注册和心跳维持
- 暴露Broker状态（StateFlow）

**状态机：**

```kotlin
enum class BrokerState {
    NEW,              // 新创建，未初始化
    INITIALIZING,     // 初始化中
    INITIALIZED,      // 初始化完成
    STARTING,         // 启动中
    RUNNING,          // 运行中
    SHUTTING_DOWN,    // 关闭中
    SHUTDOWN,         // 已关闭
    FAILED            // 失败状态
}
```

**生命周期流程：**

```
NEW ─────────> INITIALIZING ─────> INITIALIZED
                    │                    │
                    │ (失败)              │
                    ↓                    ↓
                FAILED              STARTING
                                         │
                                         ↓
                                     RUNNING
                                         │
                                         ↓
                                 SHUTTING_DOWN
                                         │
                                         ↓
                                    SHUTDOWN
```

**启动顺序：**

```
Phase 1: 初始化
1. 加载配置文件
2. 初始化MessageStoreActor（底层存储）
3. 初始化TopicManagerActor（依赖MessageStore）
4. 初始化OffsetManagerActor（Offset持久化）
5. 初始化ConsumerManagerActor（依赖OffsetManager）
6. 初始化RequestDispatcherActor（依赖所有Manager）
7. 初始化RemotingServer（网络层）
8. 初始化NameServerClient（用于注册）

Phase 2: 启动
1. 启动存储Actor
2. 启动管理Actor
3. 启动请求分发器
4. 启动Netty Server
5. 注册到NameServer
6. 启动后台任务（心跳、清理、统计）
```

**关闭顺序（逆序）：**

```
1. 停止接收新请求（关闭Netty监听）
2. 等待处理中的请求完成
3. 向NameServer注销
4. 持久化Offset
5. 刷盘（确保数据落盘）
6. 关闭子Actor（逆序）
7. 取消所有协程
```

**关键代码结构：**

```kotlin
class BrokerController(
    private val brokerConfig: BrokerConfig,
    private val nettyServerConfig: NettyServerConfig,
    private val nameServerAddresses: List<String>
) {
    // 状态管理
    private val _state = MutableStateFlow<BrokerState>(BrokerState.NEW)
    val state: StateFlow<BrokerState> = _state.asStateFlow()

    // 协程作用域（SupervisorJob确保子协程失败不影响其他）
    private val brokerScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("BrokerController")
    )

    // 初始化
    suspend fun initialize(): Boolean

    // 启动
    suspend fun start(): Boolean

    // 关闭
    suspend fun shutdown()
}
```

**设计要点：**

1. **状态转换原子性** - 使用Mutex确保状态转换的线程安全
2. **错误处理和回滚** - 初始化失败时清理已创建资源
3. **SupervisorJob** - 子协程失败不影响其他子协程
4. **优雅关闭** - 确保数据不丢失

---

### 2. RequestDispatcherActor - 请求分发

**职责：**
- 接收Netty层的请求
- 根据RequestCode路由到对应Processor
- 实现背压控制（防止过载）

**接口设计：**

```kotlin
class RequestDispatcherActor(
    private val messageStore: MessageStoreActor,
    private val topicManager: TopicManagerActor,
    private val consumerManager: ConsumerManagerActor,
    private val config: DispatcherConfig = DispatcherConfig()
) {
    // 请求Channel（有界，实现背压）
    private val requestChannel = Channel<DispatchRequest>(
        capacity = config.maxPendingRequests
    )

    // Processor映射表
    private val processorMap = mutableMapOf<Int, ActorProcessor>()

    // 统计
    private val _totalRequestCount = MutableStateFlow(0L)
    private val _pendingRequestCount = MutableStateFlow(0)

    // 提交请求（suspend，支持背压）
    suspend fun dispatch(request: DispatchRequest)
}
```

**背压机制：**

```kotlin
data class DispatcherConfig(
    val maxPendingRequests: Int = 10000  // 最多1万待处理请求
)
```

当Channel满时，`send()`会挂起，自然实现背压。

**Processor注册：**

```kotlin
RequestCode.SEND_MESSAGE -> SendMessageProcessor
RequestCode.PULL_MESSAGE -> PullMessageProcessor
RequestCode.HEART_BEAT -> HeartbeatProcessor
RequestCode.UPDATE_CONSUMER_OFFSET -> UpdateOffsetProcessor
RequestCode.QUERY_CONSUMER_OFFSET -> QueryOffsetProcessor
RequestCode.UPDATE_AND_CREATE_TOPIC -> CreateTopicProcessor
```

---

### 3. TopicManagerActor - Topic管理

**职责：**
- Topic元数据管理
- Queue分配（Round-Robin）
- 自动创建Topic

**核心数据结构：**

```kotlin
// Topic配置表（内存缓存）
private val topicConfigTable = ConcurrentHashMap<String, TopicConfig>()

// 数据版本（用于同步）
private val _dataVersion = MutableStateFlow(DataVersion())

// Queue分配计数器（Round-Robin）
private val queueIdCounter = AtomicInteger(0)
```

**主要接口：**

```kotlin
// 获取Topic配置
fun getTopicConfig(topic: String): TopicConfig?

// 创建Topic
suspend fun createTopic(
    topic: String,
    readQueueNums: Int = 8,
    writeQueueNums: Int = 8,
    perm: Int = 6
): Boolean

// 选择QueueId（Round-Robin）
fun selectQueueId(topic: String): Int

// 是否允许自动创建Topic
fun isAutoCreateTopicEnabled(): Boolean
```

---

### 4. ConsumerManagerActor - 消费者管理

**职责：**
- 消费者组注册
- Offset管理
- 过期消费者清理

**核心数据结构：**

```kotlin
// 消费者组表（Group -> ConsumerGroupInfo）
private val consumerGroupTable = ConcurrentHashMap<String, ConsumerGroupInfo>()

// Channel -> Group映射（用于断线清理）
private val channelGroupTable = ConcurrentHashMap<Channel, String>()
```

**主要接口：**

```kotlin
// 注册消费者
suspend fun registerConsumer(
    group: String,
    clientId: String,
    channel: Channel
)

// 更新Offset
suspend fun updateOffset(
    group: String,
    topic: String,
    queueId: Int,
    offset: Long
): Boolean

// 查询Offset
suspend fun queryOffset(
    group: String,
    topic: String,
    queueId: Int
): Long

// 持久化所有Offset
suspend fun persistAllOffsets()

// 清理过期消费者
suspend fun cleanExpiredConsumer()
```

---

## 存储引擎设计

### 文件组织结构

```
store/
├── commitlog/
│   ├── 00000000000000000000.bytes  (1GB)
│   ├── 00000000001073741824.bytes  (1GB)
│   └── ...
├── consumequeue/
│   ├── TopicA/
│   │   ├── 0/
│   │   │   ├── 00000000000000000000.bytes
│   │   │   └── ...
│   │   ├── 1/
│   │   └── 2/
│   └── TopicB/
│       ├── 0/
│       └── ...
├── index/
│   ├── 00000000000000000000.bytes
│   └── ...
└── config/
    ├── topics.json
    └── consumerOffset.json
```

### 核心概念

1. **CommitLog** - 所有消息的顺序写文件（类似WAL）
2. **ConsumeQueue** - 逻辑队列，存储消息在CommitLog中的偏移量
3. **IndexFile** - 按消息Key索引，支持快速查询（可选）

### MappedFile - 文件内存映射

**核心思想：** 使用`FileChannel.map()`将文件映射到内存，实现零拷贝读写。

```kotlin
class MappedFile(
    val fileName: String,
    val fileSize: Int
) {
    // 内存映射
    private val mappedByteBuffer: MappedByteBuffer

    // 当前写入位置（原子操作，协程安全）
    private val wrotePosition = AtomicInteger(0)

    // 已刷盘位置
    private val flushedPosition = AtomicInteger(0)

    // 追加消息
    suspend fun appendMessage(data: ByteBuffer): AppendMessageResult

    // 读取消息
    fun getMessage(offset: Int, size: Int): ByteBuffer?

    // 刷盘
    suspend fun flush(flushLeastPages: Int = 0): Boolean
}
```

**消息格式：**

```
[totalSize][magicCode][bodyCRC][queueId][flag][sysFlag][queueOffset]
[bodySize][body][propertiesSize][properties]
```

### CommitLogActor - 消息顺序写日志

**职责：**
- 管理所有MappedFile
- 顺序写入消息
- 提供消息查询接口

```kotlin
class CommitLogActor(
    private val storePath: String,
    private val mappedFileSize: Int = 1024 * 1024 * 1024  // 1GB
) {
    // MappedFile队列
    private val mappedFileQueue = MappedFileQueue(
        storePath + "/commitlog",
        mappedFileSize
    )

    // 最大偏移量
    private val _maxOffset = MutableStateFlow(0L)

    // 追加消息
    suspend fun appendMessage(messageExt: MessageExt): AppendMessageResult

    // 根据物理偏移量读取消息
    suspend fun getMessage(phyOffset: Long): MessageExt?

    // 刷盘
    suspend fun flush(flushLeastPages: Int = 0): Boolean
}
```

**写入流程：**

```
1. 获取或创建MappedFile
2. 编码消息为ByteBuffer
3. 写入文件（顺序写）
4. 更新maxOffset
5. 返回结果
```

### ConsumeQueueBuilderActor - 消费队列索引

**职责：**
- 存储消息在CommitLog中的偏移量
- 按Topic-QueueId组织
- 提供快速消费查询

```kotlin
class ConsumeQueueBuilderActor(
    private val storePath: String,
    private val mappedFileSize: Int = 1024 * 1024 * 6  // 6MB
) {
    // ConsumeQueue缓存（Topic-QueueId -> ConsumeQueue）
    private val consumeQueueTable = mutableMapOf<String, ConsumeQueue>()

    // 构建ConsumeQueue索引
    suspend fun buildConsumeQueue(
        topic: String,
        queueId: Int,
        phyOffset: Long,
        size: Int,
        tagsCode: Long = 0
    )

    // 获取消息偏移量列表
    suspend fun getPhysicOffsets(
        topic: String,
        queueId: Int,
        startOffset: Long,
        maxNums: Int
    ): List<Long>
}
```

**索引单元格式：**

```
[phyOffset(8字节)][size(4字节)][tagsCode(8字节)] = 20字节
```

### MessageStoreActor - 存储总协调

```kotlin
class MessageStoreActor(
    private val storePath: String,
    private val config: StoreConfig
) {
    // 子Actor
    private lateinit var commitLog: CommitLogActor
    private lateinit var consumeQueueBuilder: ConsumeQueueBuilderActor

    // 请求Channel
    private val requestChannel = Channel<StoreRequest>(Channel.UNLIMITED)

    // 消息计数
    private val _messageCount = MutableStateFlow(0L)

    // 存储消息
    suspend fun putMessage(message: MessageExt): PutMessageResult

    // 获取消息
    suspend fun getMessage(
        topic: String,
        queueId: Int,
        offset: Long,
        maxMsgNums: Int
    ): List<MessageExt>

    // 刷盘
    suspend fun flush(): Job
}
```

**存储流程：**

```
1. 写入CommitLog（同步）
   ↓
2. 构建ConsumeQueue（异步）
   ↓
3. 更新消息计数
   ↓
4. 返回结果
```

**读取流程：**

```
1. 从ConsumeQueue获取物理偏移量
   ↓
2. 从CommitLog读取消息
   ↓
3. 返回消息列表
```

---

## 请求处理流程

### 发送消息流程

```
Producer → Netty → BrokerRequestProcessor
                         ↓
                RequestDispatcherActor
                         ↓
                  SendMessageProcessor
                         ↓
                   1. 检查Topic（TopicManagerActor）
                   2. 分配QueueId
                   3. 存储消息（MessageStoreActor）
                         ↓
                  CommitLog.appendMessage()
                         ↓
                  ConsumeQueueBuilder.build()
                         ↓
                  返回响应 → Producer
```

**SendMessageProcessor伪代码：**

```kotlin
override suspend fun process(request: DispatchRequest): RemotingCommand {
    // 1. 解析请求头
    val requestHeader = parseRequestHeader(request)

    // 2. 检查Topic是否存在
    val topicConfig = topicManager.getTopicConfig(topic)
        ?: autoCreateTopic(topic)

    // 3. 分配QueueId
    val queueId = selectQueueId(topic)

    // 4. 构建MessageExt
    val messageExt = buildMessage(requestHeader)

    // 5. 存储消息
    val putResult = messageStore.putMessage(messageExt)

    // 6. 返回响应
    return buildResponse(putResult)
}
```

### 拉取消息流程

```
Consumer → Netty → BrokerRequestProcessor
                         ↓
                RequestDispatcherActor
                         ↓
                  PullMessageProcessor
                         ↓
                   1. 注册消费者（ConsumerManagerActor）
                   2. 查询Offset（OffsetManager）
                   3. 拉取消息（MessageStoreActor）
                         ↓
                  ConsumeQueue.getOffsets()
                         ↓
                  CommitLog.getMessage()
                         ↓
                  返回消息 → Consumer
```

**PullMessageProcessor伪代码：**

```kotlin
override suspend fun process(request: DispatchRequest): RemotingCommand {
    // 1. 解析请求头
    val requestHeader = parseRequestHeader(request)

    // 2. 注册消费者
    consumerManager.registerConsumer(group, clientId, channel)

    // 3. 查询Offset
    val offset = consumerManager.queryOffset(group, topic, queueId)

    // 4. 拉取消息
    val messages = messageStore.getMessage(topic, queueId, offset, maxMsgNums)

    // 5. 构建响应
    return buildResponse(messages)
}
```

---

## 客户端设计

### Producer客户端

#### DefaultMQProducer - 生产者API

```kotlin
class DefaultMQProducer(
    private val producerGroup: String,
    private val clientConfig: ClientConfig = ClientConfig()
) {
    // 内部实现（隐藏Actor细节）
    private lateinit var producerActor: ProducerActor

    // 启动
    suspend fun start()

    // 关闭
    suspend fun shutdown()

    // 发送消息（同步）
    suspend fun send(message: Message): SendResult

    // 发送消息（异步）
    suspend fun sendAsync(message: Message, callback: SendCallback)

    // 发送单向消息
    suspend fun sendOneway(message: Message)

    // 批量发送
    suspend fun sendBatch(messages: List<Message>): SendResult
}
```

#### ProducerActor - 生产者Actor实现

```kotlin
class ProducerActor(
    private val producerGroup: String,
    private val nameServerClient: NameServerClient,
    private val clientConfig: ClientConfig
) {
    // 路由缓存（Topic -> TopicPublishInfo）
    private val topicPublishInfoTable = mutableMapOf<String, TopicPublishInfo>()

    // 请求Channel
    private val sendChannel = Channel<SendRequest>(Channel.UNLIMITED)

    // 处理发送请求
    private suspend fun processSend(request: SendRequest) {
        // 1. 获取路由
        val route = getRoute(message.topic)

        // 2. 选择Broker
        val broker = selectBroker(route)

        // 3. 发送请求（带重试）
        val result = sendWithRetry(broker, message)

        // 4. 返回结果
        request.deferred.complete(result)
    }

    // 重试策略
    private suspend fun sendWithRetry(
        broker: BrokerData,
        message: Message
    ): SendResult {
        repeat(clientConfig.retryTimes) { attempt ->
            try {
                return sendToBroker(broker, message)
            } catch (e: Exception) {
                if (attempt == clientConfig.retryTimes - 1) {
                    throw e
                }
                delay(exponentialBackoff(attempt))
            }
        }
    }
}
```

### Consumer客户端

#### DefaultMQPushConsumer - 推送消费者API

```kotlin
class DefaultMQPushConsumer(
    private val consumerGroup: String,
    private val clientConfig: ClientConfig = ClientConfig()
) {
    // 内部Actor
    private lateinit var consumerActor: ConsumerActor
    private lateinit var rebalanceActor: RebalanceActor

    // 消息监听器
    private var messageListener: MessageListener? = null

    // 订阅Topic
    fun subscribe(topic: String, subExpression: String = "*")

    // 设置消息监听器
    fun setMessageListener(listener: MessageListener)

    // 启动
    suspend fun start()

    // 关闭
    suspend fun shutdown()
}
```

#### ConsumerActor - 消费者Actor实现

```kotlin
class ConsumerActor(
    private val consumerGroup: String,
    private val clientConfig: ClientConfig,
    private val messageListener: MessageListener
) {
    // 订阅信息
    private val subscriptionTable = mutableMapOf<String, Subscription>()

    // 分配的MessageQueue
    private val assignedQueues = MutableStateFlow<Set<MessageQueue>>(emptySet())

    // Offset存储
    private lateinit var offsetStore: OffsetStore

    // 拉取请求Channel
    private val pullChannel = Channel<PullRequest>(Channel.UNLIMITED)

    // 更新分配的队列（由Rebalance调用）
    suspend fun updateAssignedQueues(queues: Set<MessageQueue>)

    // 开始拉取指定队列
    private fun startPulling(mq: MessageQueue) {
        consumerScope.launch {
            while (isActive) {
                pullMessage(mq)
                delay(100)
            }
        }
    }

    // 拉取消息
    private suspend fun pullMessage(mq: MessageQueue) {
        // 1. 获取offset
        val offset = offsetStore.getOffset(consumerGroup, mq)

        // 2. 发送拉取请求
        val response = nettyClient.invokeSync(brokerAddr, request)

        // 3. 解析消息
        val messages = parseMessages(response)

        // 4. 提交给监听器
        for (msg in messages) {
            messageListener.consume(msg)
            offsetStore.updateOffset(consumerGroup, mq, msg.queueOffset)
        }
    }
}
```

#### RebalanceActor - 负载均衡Actor

```kotlin
class RebalanceActor(
    private val consumerGroup: String,
    private val consumerActor: ConsumerActor
) {
    // 协程作用域
    private val rebalanceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 定时任务
    private var rebalanceJob: Job? = null

    fun start() {
        rebalanceJob = rebalanceScope.launch {
            while (isActive) {
                doRebalance()
                delay(20_000)  // 每20秒重平衡
            }
        }
    }

    private suspend fun doRebalance() {
        // 1. 获取所有订阅的Topic
        val topics = getSubscribedTopics()

        // 2. 对每个Topic进行重平衡
        for (topic in topics) {
            rebalanceByTopic(topic)
        }
    }

    private suspend fun rebalanceByTopic(topic: String) {
        // 1. 获取Topic路由信息
        val route = nameServerClient.getRoute(topic)

        // 2. 获取所有消费者（从Broker）
        val consumers = getAllConsumers(topic)

        // 3. 计算队列分配
        val allocation = allocateQueue(consumers, route.queues)

        // 4. 更新ConsumerActor
        consumerActor.updateAssignedQueues(allocation)
    }
}
```

---

## 测试策略

### 测试层次

```
1. 单元测试（Unit Tests）
   - 每个Actor的独立测试
   - 存储引擎测试
   - 工具类测试

2. 集成测试（Integration Tests）
   - Broker启动流程测试
   - 发送-消费端到端测试
   - 故障恢复测试

3. 性能测试（Performance Tests）
   - 消息存储吞吐量测试
   - 并发发送测试
   - 延迟测试
```

### 单元测试示例

**MappedFile测试：**

```kotlin
@Test
fun `test append and read message`() = runBlocking {
    val mappedFile = MappedFile("/tmp/test.data", 1024)

    // 写入
    val data = ByteBuffer.wrap("Hello".toByteArray())
    val result = mappedFile.appendMessage(data)

    assertEquals(AppendMessageStatus.PUT_OK, result.status)

    // 读取
    val read = mappedFile.getMessage(0, 5)
    assertNotNull(read)

    val bytes = ByteArray(5)
    read.get(bytes)
    assertEquals("Hello", String(bytes))
}
```

### 集成测试示例

**端到端测试：**

```kotlin
@Test
fun `test send and consume message`() = runBlocking {
    // 1. 启动Broker
    val broker = startBroker()

    // 2. 启动Producer
    val producer = DefaultMQProducer("TestGroup")
    producer.start()

    // 3. 发送消息
    val message = Message("TestTopic", "Hello".toByteArray())
    val sendResult = producer.send(message)

    assertEquals(SendStatus.SEND_OK, sendResult.status)

    // 4. 启动Consumer
    var receivedMessage: MessageExt? = null
    val consumer = DefaultMQPushConsumer("TestGroup")
    consumer.setMessageListener { msg ->
        receivedMessage = msg
        ConsumeConcurrentlyStatus.CONSUME_SUCCESS
    }
    consumer.subscribe("TestTopic")
    consumer.start()

    // 5. 等待消费
    delay(2000)

    assertNotNull(receivedMessage)
    assertEquals("Hello", String(receivedMessage!!.body))
}
```

### 性能测试示例

**吞吐量测试：**

```kotlin
@Test
fun `test message store throughput`() = runBlocking {
    val store = MessageStoreActor("/tmp/store", StoreConfig())
    store.load()
    store.start()

    val messageCount = 100_000
    val startTime = System.currentTimeMillis()
    val successCount = AtomicLong(0)

    // 并发写入
    val jobs = (1..messageCount).map { i ->
        GlobalScope.async(Dispatchers.IO) {
            val msg = createTestMessage(i)
            val result = store.putMessage(msg)

            if (result.status == PutMessageStatus.PUT_OK) {
                successCount.incrementAndGet()
            }
        }
    }

    jobs.awaitAll()

    val costTime = System.currentTimeMillis() - startTime
    val tps = successCount.get() * 1000 / costTime

    println("TPS: $tps messages/sec")
    assertTrue(tps > 10_000, "TPS should > 10000")
}
```

---

## 实现计划

### 阶段1：基础存储（2周）

**目标：** 实现CommitLog和ConsumeQueue

**任务：**
1. 实现MappedFile（文件映射）
2. 实现CommitLogActor（消息追加）
3. 实现ConsumeQueueBuilderActor（索引构建）
4. 实现MessageStoreActor（协调层）
5. 编写存储层单元测试

**交付物：**
- 可工作的存储引擎
- 消息写入和读取功能
- 单元测试覆盖率 > 80%

### 阶段2：Broker核心（3周）

**目标：** 实现BrokerController和请求处理

**任务：**
1. 实现BrokerController（启动/关闭流程）
2. 实现RequestDispatcherActor（请求分发）
3. 实现SendMessageProcessor（发送消息）
4. 实现PullMessageProcessor（拉取消息）
5. 实现TopicManagerActor（Topic管理）
6. 实现ConsumerManagerActor（消费者管理）
7. 实现OffsetManager（Offset管理）
8. 编写Broker集成测试

**交付物：**
- 可启动的Broker
- 消息发送和拉取功能
- 向NameServer注册
- 集成测试通过

### 阶段3：Producer客户端（2周）

**目标：** 实现生产者客户端

**任务：**
1. 实现ProducerActor（发送逻辑）
2. 实现路由缓存和选择策略
3. 实现重试机制
4. 实现批量发送
5. 编写Producer单元测试和集成测试

**交付物：**
- 可用的Producer API
- 消息发送功能
- 重试机制
- 测试覆盖

### 阶段4：Consumer客户端（3周）

**目标：** 实现消费者客户端

**任务：**
1. 实现ConsumerActor（拉取逻辑）
2. 实现RebalanceActor（负载均衡）
3. 实现OffsetStore（Offset持久化）
4. 实现MessageListener（消息监听）
5. 实现消费进度提交
6. 编写Consumer单元测试和集成测试

**交付物：**
- 可用的Consumer API
- 消息拉取和消费功能
- 负载均衡
- 测试覆盖

### 阶段5：测试和优化（2周）

**目标：** 完整测试和性能优化

**任务：**
1. 端到端集成测试
2. 性能测试（TPS、延迟）
3. 压力测试（稳定性）
4. 性能优化
5. 文档编写

**交付物：**
- 完整的测试套件
- 性能报告
- 用户文档
- API文档

---

## 附录

### A. 包结构总览

```
broker/
├── src/main/kotlin/com/agmtopy/kocketmq/broker/
│   ├── BrokerController.kt
│   ├── BrokerStartup.kt
│   ├── actor/
│   │   ├── RequestDispatcherActor.kt
│   │   ├── SendMessageActor.kt
│   │   ├── PullMessageActor.kt
│   │   ├── AdminProcessorActor.kt
│   │   ├── TopicManagerActor.kt
│   │   └── ConsumerManagerActor.kt
│   ├── processor/
│   │   └── BrokerRequestProcessor.kt
│   ├── offset/
│   │   ├── OffsetManager.kt
│   │   └── OffsetStore.kt
│   ├── store/
│   │   ├── MessageStoreActor.kt
│   │   ├── CommitLogActor.kt
│   │   ├── ConsumeQueueBuilderActor.kt
│   │   ├── MappedFile.kt
│   │   └── MappedFileQueue.kt
│   └── config/
│       └── BrokerConfig.kt

client/
├── src/main/kotlin/com/agmtopy/kocketmq/client/
│   ├── producer/
│   │   ├── DefaultMQProducer.kt
│   │   ├── ProducerActor.kt
│   │   ├── TopicPublishInfo.kt
│   │   └── RetryPolicy.kt
│   ├── consumer/
│   │   ├── DefaultMQPushConsumer.kt
│   │   ├── ConsumerActor.kt
│   │   ├── RebalanceActor.kt
│   │   ├── OffsetStore.kt
│   │   └── MessageListener.kt
│   ├── impl/
│   │   ├── ClientRemotingProcessor.kt
│   │   ├── MQClientActor.kt
│   │   └── NameServerClient.kt
│   └── config/
│       └── ClientConfig.kt
```

### B. 配置参数

**BrokerConfig：**

```kotlin
data class BrokerConfig(
    val brokerName: String = "DefaultBroker",
    val brokerId: Long = 0,  // 0=Master
    val clusterName: String = "DefaultCluster",
    val listenPort: Int = 10911,
    val namesrvAddr: String = "",

    // 存储配置
    val storePathRootDir: String = "/tmp/kocketmq/store",
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
    val autoCreateTopicEnable: Boolean = true
)
```

**ClientConfig：**

```kotlin
data class ClientConfig(
    val nameServerAddresses: List<String> = emptyList(),
    val instanceName: String = "DEFAULT",

    // 发送配置
    val retryTimes: Int = 3,
    val sendTimeout: Long = 3000,

    // 拉取配置
    val pullTimeout: Long = 3000,
    val pullBatchSize: Int = 32,

    // 消费配置
    val consumeThreadMin: Int = 4,
    val consumeThreadMax: Int = 16
)
```

### C. 性能指标

**目标性能：**

| 指标 | 目标值 |
|------|--------|
| 单机TPS | > 10万/秒 |
| 发送延迟P99 | < 10ms |
| 拉取延迟P99 | < 5ms |
| 消息可靠性 | 99.99% |
| 内存占用 | < 4GB |
| CPU利用率 | < 80% |

### D. 关键技术点

**1. 协程最佳实践**

```kotlin
// ✅ 正确：使用suspend函数
suspend fun putMessage(message: Message): Result

// ✅ 正确：使用Channel通信
private val channel = Channel<Request>()

// ❌ 错误：使用锁
private val lock = ReentrantLock()

// ✅ 正确：Actor内部串行处理，无需锁
for (request in channel) {
    process(request)  // 串行
}
```

**2. 背压控制**

```kotlin
// ✅ 正确：有界Channel
private val channel = Channel<Request>(capacity = 1000)

// 当Channel满时，send会挂起（背压）
suspend fun send(request: Request) {
    channel.send(request)  // 可能挂起
}
```

**3. 优雅关闭**

```kotlin
suspend fun shutdown() {
    // 1. 停止接收
    server.shutdown()

    // 2. 等待处理完成
    waitForPendingRequests()

    // 3. 持久化数据
    persistData()

    // 4. 取消协程
    scope.cancel()
}
```

---

## 总结

本设计文档详细描述了KocketMQ Broker核心的架构和实现方案：

**核心特点：**

1. **完全协程化** - 100%使用Kotlin协程，无阻塞调用
2. **Actor模型** - 无锁并发，通过Channel通信
3. **高性能存储** - 重写CommitLog/ConsumeQueue，零拷贝
4. **协议兼容** - 兼容RocketMQ协议
5. **易于测试** - 清晰的分层和接口

**下一步：**

1. 审核本设计文档
2. 创建详细的实现计划
3. 开始阶段1的开发

---

**文档版本历史：**

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| 1.0 | 2026-03-20 | 初始版本 |
