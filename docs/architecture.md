# KocketMQ 架构设计文档

## 1. 整体架构

KocketMQ 采用分层架构设计，从下到上依次为：存储层、网络层、处理层、管理层。

```
┌─────────────────────────────────────────────────────────┐
│                      管理层                              │
│  TopicConfigManager | ConsumerOffsetManager | BrokerStats│
└─────────────────────────────────────────────────────────┘
                            ▲
                            │
┌─────────────────────────────────────────────────────────┐
│                      处理层                              │
│  SendMessageProcessor | PullMessageProcessor            │
│  AdminBrokerProcessor | QueryMessageProcessor            │
│  BatchSendMessageProcessor                               │
└─────────────────────────────────────────────────────────┘
                            ▲
                            │
┌─────────────────────────────────────────────────────────┐
│                      网络层                              │
│              NettyRemotingServer                         │
│          (RemotingCommand | 编解码)                       │
└─────────────────────────────────────────────────────────┘
                            ▲
                            │
┌─────────────────────────────────────────────────────────┐
│                      存储层                              │
│  MessageStoreActor                                       │
│       ├── CommitLogActor (顺序写入)                      │
│       └── ConsumeQueueBuilderActor (索引构建)            │
└─────────────────────────────────────────────────────────┘
```

## 2. 存储引擎设计

### 2.1 CommitLog

**职责**：所有消息按到达顺序写入 CommitLog 文件，实现顺序写入，性能最优。

**文件组织**：
```
/commitlog/
├── 00000000000000000000.bytes  // 第一个文件，0-1GB
├── 00000000001073741824.bytes  // 第二个文件，1GB-2GB
└── ...
```

**消息格式**（每条消息）：
```
┌──────────────────────────────────────────────────┐
│ TotalSize (4字节)                                 │
│ MagicCode (4字节)                                 │
│ BodyCRC (4字节)                                   │
│ QueueId (4字节)                                   │
│ Flag (4字节)                                      │
│ QueueOffset (8字节)                               │
│ PhysicalOffset (8字节)                            │
│ SystemTimestamp (8字节)                           │
│ BornTimestamp (8字节)                             │
│ BornHost (8字节)                                  │
│ StoreTimestamp (8字节)                            │
│ StoreHost (8字节)                                 │
│ ReconsumeTimes (4字节)                            │
│ PreparedTransactionOffset (8字节)                 │
│ BodyLength (4字节) + Body (变长)                  │
│ TopicLength (1字节) + Topic (变长)                │
│ PropertiesLength (2字节) + Properties (变长)      │
└──────────────────────────────────────────────────┘
```

**性能优化**：
- 内存映射文件（MMAP）：避免用户态和内核态的数据拷贝
- 顺序写入：磁盘顺序写入性能远高于随机写入
- 文件预热：启动时预热文件，避免运行时缺页中断

### 2.2 ConsumeQueue

**职责**：为每个 Topic-Queue 建立索引，支持快速消息查找。

**文件组织**：
```
/consumequeue/
├── TopicA/
│   ├── 0/
│   │   ├── 00000000000000000000.bytes  // Queue 0 的索引文件
│   │   └── ...
│   ├── 1/
│   └── ...
└── TopicB/
    └── ...
```

**索引单元格式**（20 字节）：
```
┌────────────────────────────────────────┐
│ CommitLog Physical Offset (8字节)      │
│ Message Size (4字节)                    │
│ Tags Hash Code (8字节)                  │
└────────────────────────────────────────┘
```

**查询流程**：
1. 根据逻辑偏移量计算物理偏移量：`physicalOffset = logicOffset * 20`
2. 从 ConsumeQueue 文件读取 20 字节
3. 解析出 CommitLog 物理偏移量和消息大小
4. 从 CommitLog 读取完整消息

### 2.3 Actor 模型实现

**设计理念**：使用协程 Channel 实现消息驱动的 Actor 模型，避免显式锁。

```kotlin
class CommitLogActor {
    // 请求通道，所有写操作都通过此通道串行化
    private val requestChannel = Channel<Request>(Channel.UNLIMITED)

    suspend fun putMessage(message: MessageExt): PutMessageResult {
        val response = CompletableDeferred<PutMessageResult>()
        requestChannel.send(Request.Put(message, response))
        return response.await()
    }

    // 协程处理通道中的请求
    private fun processRequests() = scope.launch {
        for (request in requestChannel) {
            when (request) {
                is Request.Put -> {
                    val result = doPutMessage(request.message)
                    request.response.complete(result)
                }
            }
        }
    }
}
```

**优势**：
- 无锁并发：所有写操作通过 Channel 串行化，无需显式锁
- 背压控制：Channel 支持背压，避免内存溢出
- 错误隔离：每个 Actor 独立，错误不会传播
- 易于测试：Actor 的行为可预测，易于验证

## 3. 网络层设计

### 3.1 协议设计

**RemotingCommand 结构**：
```
┌────────────────────────────────────────┐
│ Code (4字节) - 请求/响应码              │
│ Language (1字节) - 语言类型             │
│ Version (2字节) - 协议版本              │
│ Opaque (4字节) - 请求标识               │
│ Flag (4字节) - 标志位                   │
│ RemarkLength (4字节) + Remark (变长)    │
│ ExtFieldsLength (4字节) + ExtFields     │
│ BodyLength (4字节) + Body (变长)        │
└────────────────────────────────────────┘
```

**请求码示例**：
```kotlin
object RequestCode {
    const val SEND_MESSAGE = 10
    const val PULL_MESSAGE = 11
    const val QUERY_MESSAGE = 12
    const val UPDATE_AND_CREATE_TOPIC = 17
    const val GET_BROKER_CONFIG = 35
    // ...
}
```

### 3.2 Netty 集成

**ChannelPipeline 配置**：
```kotlin
pipeline.addLast("decoder", NettyDecoder())
pipeline.addLast("encoder", NettyEncoder())
pipeline.addLast("handler", NettyServerHandler())
```

**性能优化**：
- 零拷贝：使用 FileRegion 实现文件传输零拷贝
- 内存池：使用 Netty 的 PooledByteBufAllocator
- 批量读写：支持批量消息处理

## 4. 处理层设计

### 4.1 消息发送流程

```
Producer
    │
    │ SEND_MESSAGE Request
    ▼
SendMessageProcessor
    │
    │ 1. 解析请求
    │ 2. 创建 MessageExt
    │ 3. 自动创建 Topic（如果启用）
    ▼
MessageStoreActor
    │
    │ 4. 写入 CommitLog
    │ 5. 异步构建 ConsumeQueue 索引
    ▼
Response (msgId, queueOffset)
    │
    ▼
Producer
```

### 4.2 消息拉取流程

```
Consumer
    │
    │ PULL_MESSAGE Request
    ▼
PullMessageProcessor
    │
    │ 1. 解析请求
    │ 2. 验证 Topic 和 Queue
    ▼
MessageStoreActor
    │
    │ 3. 从 ConsumeQueue 查询索引
    │ 4. 从 CommitLog 读取消息
    ▼
Response (messages, nextOffset)
    │
    ▼
Consumer
```

## 5. 配置管理设计

### 5.1 TopicConfigManager

**职责**：管理 Topic 配置，支持动态创建、更新、删除。

**数据结构**：
```kotlin
data class TopicConfig(
    val topicName: String,
    val readQueueNums: Int = 8,
    val writeQueueNums: Int = 8,
    val perm: Int = PermName.PERM_READ or PermName.PERM_WRITE,
    val topicFilterType: TopicFilterType = TopicFilterType.SINGLE_TAG,
    val topicSysFlag: Int = 0,
    val order: Boolean = false
)
```

**持久化**：
- 格式：JSON
- 路径：`{storePath}/config/topics.json`
- 写入策略：原子写入（先写临时文件，再重命名）

### 5.2 ConsumerOffsetManager

**职责**：追踪消费者组的消费进度。

**数据结构**：
```kotlin
// Map<Topic, Map<QueueId, Map<Group, Offset>>>
private val offsetTable = ConcurrentHashMap<String, ConcurrentHashMap<Int, ConcurrentHashMap<String, Long>>>()
```

**持久化**：
- 格式：JSON
- 路径：`{storePath}/config/consumerOffset.json`
- 写入策略：定时持久化（可配置间隔）

## 6. 性能优化策略

### 6.1 内存映射文件

**原理**：将文件映射到内存，避免用户态和内核态的数据拷贝。

```kotlin
val mappedByteBuffer = fileChannel.map(
    FileChannel.MapMode.READ_WRITE,
    position,
    size
)
```

**优势**：
- 减少数据拷贝：直接在内存中操作文件
- 操作系统自动管理：页缓存、预读
- 随机访问性能好

### 6.2 批量处理

**批量发送**：
```kotlin
class BatchSendMessageProcessor {
    suspend fun processBatch(messages: List<MessageExt>) {
        // 一次性编码所有消息
        val batchData = encodeBatch(messages)

        // 一次性写入 CommitLog
        messageStore.putMessages(batchData)
    }
}
```

**批量拉取**：
```kotlin
suspend fun getMessages(
    topic: String,
    queueId: Int,
    startOffset: Long,
    maxNums: Int = 32
): List<MessageExt>
```

### 6.3 消息压缩

**策略**：消息大小超过阈值时自动压缩。

```kotlin
if (message.body.size > compressThreshold) {
    message.body = MessageCompressor.compress(message.body)
    message.sysFlag = message.sysFlag or CompressFlag.GZIP
}
```

**压缩算法**：GZIP（可扩展支持其他算法）

## 7. 故障恢复

### 7.1 CommitLog 恢复

**流程**：
1. 扫描最后一个 CommitLog 文件
2. 读取文件末尾的写位置
3. 验证消息完整性（CRC 校验）
4. 恢复写位置

### 7.2 ConsumeQueue 恢复

**流程**：
1. 从 CommitLog 重放消息
2. 重新构建 ConsumeQueue 索引
3. 恢复逻辑偏移量

## 8. 监控与统计

### 8.1 BrokerStats

**统计指标**：
```kotlin
class BrokerStats {
    // 消息发送统计
    val sendmessageNums = AtomicLong(0)
    val sendmessageFailedNums = AtomicLong(0)

    // 消息拉取统计
    val pullMessageNums = AtomicLong(0)
    val pullMessageFailedNums = AtomicLong(0)

    // TPS 统计
    fun getSendTPS(): Double
    fun getPullTPS(): Double
}
```

### 8.2 性能监控

**关键指标**：
- 发送 TPS、拉取 TPS
- 平均延迟、P99 延迟
- 磁盘使用率
- 内存使用率
- 协程数量

## 9. 扩展性设计

### 9.1 插件机制（计划）

**目标**：支持通过插件扩展功能，如：
- TLS 安全插件
- 消息轨迹追踪插件
- 自定义消息过滤插件

### 9.2 HA 高可用（计划）

**Master-Slave 架构**：
- Master：处理读写请求
- Slave：复制 Master 数据，提供读服务
- 同步复制：保证数据不丢失
- 异步复制：提高性能

## 10. 未来规划

### 10.1 事务消息

**设计**：
- 半消息：消息暂不投递
- 事务状态：提交/回滚
- 事务协调器：管理事务状态

### 10.2 延迟消息

**设计**：
- 延迟级别：1s、5s、10s、30s、1m、2m...
- 定时任务调度器
- 延迟消息存储

### 10.3 消息轨迹

**设计**：
- 记录消息全链路信息
- 存储到独立的 Trace Topic
- 提供查询接口

---

**文档版本**：v1.0
**最后更新**：2026-03-21
**维护者**：KocketMQ 团队
