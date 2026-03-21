# KocketMQ 配置说明文档

## 1. Broker 配置

### 1.1 BrokerConfig 配置项

```kotlin
data class BrokerConfig(
    // ==================== 基础配置 ====================

    /**
     * Broker 名称
     * 默认值: "DefaultBroker"
     */
    val brokerName: String = "DefaultBroker",

    /**
     * Broker ID
     * 0 = Master
     * 其他 = Slave
     * 默认值: 0
     */
    val brokerId: Long = 0,

    /**
     * 集群名称
     * 默认值: "DefaultCluster"
     */
    val clusterName: String = "DefaultCluster",

    /**
     * 监听端口
     * 默认值: 10911
     */
    val listenPort: Int = 10911,

    /**
     * NameServer 地址
     * 格式: "ip:port"
     * 默认值: "" (不注册到 NameServer)
     */
    val namesrvAddr: String = "",

    // ==================== 存储配置 ====================

    /**
     * 存储根目录
     * 默认值: "/tmp/kocketmq/store"
     */
    val storePathRootDir: String = "/tmp/kocketmq/store",

    /**
     * CommitLog 存储目录
     * 默认值: "/tmp/kocketmq/store/commitlog"
     */
    val storePathCommitLog: String = "/tmp/kocketmq/store/commitlog",

    /**
     * CommitLog 文件大小（字节）
     * 建议: 1GB
     * 默认值: 1073741824 (1GB)
     */
    val commitLogFileSize: Int = 1024 * 1024 * 1024,

    /**
     * ConsumeQueue 文件大小（字节）
     * 默认值: 6291456 (6MB)
     */
    val mappedFileSizeConsumeQueue: Int = 1024 * 1024 * 6,

    // ==================== 处理配置 ====================

    /**
     * 处理线程数
     * 默认值: 16
     */
    val processThreads: Int = 16,

    /**
     * 最大挂起请求数
     * 默认值: 10000
     */
    val maxPendingRequests: Int = 10000,

    // ==================== 心跳配置 ====================

    /**
     * 心跳间隔（毫秒）
     * 默认值: 30000 (30秒)
     */
    val heartbeatIntervalMs: Long = 30_000,

    // ==================== 消费者配置 ====================

    /**
     * 消费者过期时间（毫秒）
     * 默认值: 120000 (2分钟)
     */
    val consumerExpiredTimeMs: Long = 120_000,

    // ==================== Topic 配置 ====================

    /**
     * 是否启用自动创建 Topic
     * 默认值: true
     */
    val autoCreateTopicEnable: Boolean = true,

    /**
     * 默认 Topic 队列数量
     * 默认值: 8
     */
    val defaultTopicQueueNums: Int = 8,

    // ==================== 配置文件路径 ====================

    /**
     * Broker 配置文件路径
     * 默认值: ""
     */
    val brokerConfigPath: String = "",

    /**
     * Topic 配置文件路径
     * 默认值: "/tmp/kocketmq/store/config/topics.json"
     */
    val topicConfigPath: String = "/tmp/kocketmq/store/config/topics.json",

    /**
     * 消费者 Offset 配置文件路径
     * 默认值: "/tmp/kocketmq/store/config/consumerOffset.json"
     */
    val consumerOffsetPath: String = "/tmp/kocketmq/store/config/consumerOffset.json"
)
```

### 1.2 配置示例

#### 1.2.1 基础配置

```kotlin
val brokerConfig = BrokerConfig(
    brokerName = "Broker-A",
    brokerId = 0,
    clusterName = "ProductionCluster",
    listenPort = 10911,
    namesrvAddr = "127.0.0.1:9876"
)
```

#### 1.2.2 高性能配置

```kotlin
val brokerConfig = BrokerConfig(
    brokerName = "HighPerfBroker",
    listenPort = 10911,
    storePathRootDir = "/data/kocketmq/store",

    // 大文件提升性能
    commitLogFileSize = 1024 * 1024 * 1024 * 2,  // 2GB

    // 更多处理线程
    processThreads = 32,
    maxPendingRequests = 50000,

    // 自动创建 Topic
    autoCreateTopicEnable = true,
    defaultTopicQueueNums = 16
)
```

#### 1.2.3 测试环境配置

```kotlin
val brokerConfig = BrokerConfig(
    brokerName = "TestBroker",
    listenPort = 10919,  // 使用不同端口避免冲突
    storePathRootDir = "/tmp/kocketmq/test",
    commitLogFileSize = 1024 * 1024 * 100,  // 100MB
    autoCreateTopicEnable = true
)
```

## 2. 存储配置

### 2.1 StoreConfig 配置项

```kotlin
data class StoreConfig(
    /**
     * CommitLog 文件大小（字节）
     * 默认值: 1073741824 (1GB)
     */
    val commitLogFileSize: Int = 1024 * 1024 * 1024,

    /**
     * ConsumeQueue 文件大小（字节）
     * 默认值: 6291456 (6MB)
     */
    val mappedFileSizeConsumeQueue: Int = 1024 * 1024 * 6,

    /**
     * CommitLog 刷盘间隔（提交次数）
     * 每隔多少次提交刷盘一次
     * 默认值: 1000
     */
    val flushIntervalCommits: Int = 1000,

    /**
     * ConsumeQueue 刷盘间隔
     * 默认值: 1000
     */
    val flushIntervalConsumeQueue: Int = 1000
)
```

### 2.2 配置建议

#### 2.2.1 根据磁盘类型选择文件大小

**SSD 固态硬盘**：
```kotlin
commitLogFileSize = 1024 * 1024 * 1024  // 1GB，SSD 随机读写性能好
```

**HDD 机械硬盘**：
```kotlin
commitLogFileSize = 1024 * 1024 * 1024 * 2  // 2GB，减少文件数量，提升顺序写入性能
```

#### 2.2.2 根据可靠性要求选择刷盘间隔

**高可靠性**：
```kotlin
flushIntervalCommits = 1  // 每次提交都刷盘
```

**高性能**：
```kotlin
flushIntervalCommits = 1000  // 每1000次提交刷盘一次
```

**平衡模式**：
```kotlin
flushIntervalCommits = 100  // 每100次提交刷盘一次
```

## 3. Topic 配置

### 3.1 TopicConfig 配置项

```kotlin
data class TopicConfig(
    /**
     * Topic 名称
     */
    val topicName: String,

    /**
     * 读队列数量
     * 决定消费者的并发度
     * 默认值: 8
     */
    val readQueueNums: Int = 8,

    /**
     * 写队列数量
     * 决定生产者的并发度
     * 默认值: 8
     */
    val writeQueueNums: Int = 8,

    /**
     * 权限
     * 2 = 只写
     * 4 = 只读
     * 6 = 读写
     * 默认值: 6
     */
    val perm: Int = PermName.PERM_READ or PermName.PERM_WRITE,

    /**
     * Topic 过滤类型
     * SINGLE_TAG: 单标签过滤
     * MULTI_TAG: 多标签过滤
     * 默认值: SINGLE_TAG
     */
    val topicFilterType: TopicFilterType = TopicFilterType.SINGLE_TAG,

    /**
     * Topic 系统标志
     * 默认值: 0
     */
    val topicSysFlag: Int = 0,

    /**
     * 是否顺序消息
     * true: 顺序消息
     * false: 并发消息
     * 默认值: false
     */
    val order: Boolean = false
)
```

### 3.2 配置示例

#### 3.2.1 普通消息 Topic

```kotlin
val topicConfig = TopicConfig(
    topicName = "NormalTopic",
    readQueueNums = 8,
    writeQueueNums = 8,
    perm = 6,
    order = false
)
```

#### 3.2.2 高并发 Topic

```kotlin
val topicConfig = TopicConfig(
    topicName = "HighConcurrencyTopic",
    readQueueNums = 32,  // 高并发读
    writeQueueNums = 32,  // 高并发写
    perm = 6,
    order = false
)
```

#### 3.2.3 顺序消息 Topic

```kotlin
val topicConfig = TopicConfig(
    topicName = "OrderedMessageTopic",
    readQueueNums = 1,  // 只有一个队列保证顺序
    writeQueueNums = 1,
    perm = 6,
    order = true  // 顺序消息
)
```

#### 3.2.4 只读 Topic

```kotlin
val topicConfig = TopicConfig(
    topicName = "ReadOnlyTopic",
    readQueueNums = 8,
    writeQueueNums = 0,  // 不允许写入
    perm = 4,  // 只读
    order = false
)
```

## 4. Netty 配置

### 4.1 NettyServerConfig

```kotlin
class NettyServerConfig {
    /**
     * 监听端口
     */
    var listenPort: Int = 0

    /**
     * 工作线程数
     */
    var serverWorkerThreads: Int = 8

    /**
     * Selector 线程数
     */
    var serverSelectorThreads: Int = 3

    /**
     * 单个连接最大并发数
     */
    var serverOnewaySemaphoreValue: Int = 256

    /**
     * 异步调用最大并发数
     */
    var serverAsyncSemaphoreValue: Int = 64

    /**
     * 连接空闲超时（秒）
     */
    var serverChannelMaxIdleTimeSeconds: Int = 120

    /**
     * Socket 发送缓冲区大小
     */
    var serverSocketSndBufSize: Int = 65535

    /**
     * Socket 接收缓冲区大小
     */
    var serverSocketRcvBufSize: Int = 65535

    /**
     * 写缓冲区水位（低水位）
     */
    var writeBufferLowWaterMark: Int = 32768

    /**
     * 写缓冲区水位（高水位）
     */
    var writeBufferHighWaterMark: Int = 1048576
}
```

### 4.2 配置示例

```kotlin
val nettyConfig = NettyServerConfig()
nettyConfig.listenPort = 10911
nettyConfig.serverWorkerThreads = 16
nettyConfig.serverSelectorThreads = 4
nettyConfig.serverSocketSndBufSize = 131072  // 128KB
nettyConfig.serverSocketRcvBufSize = 131072  // 128KB
```

## 5. 压缩配置

### 5.1 压缩阈值

```kotlin
/**
 * 消息压缩阈值（字节）
 * 消息大小超过此值时自动压缩
 */
const val COMPRESS_THRESHOLD = 4096  // 4KB
```

### 5.2 压缩级别

```kotlin
import java.util.zip.Deflater

/**
 * 压缩级别
 * Deflater.BEST_SPEED: 最快速度，压缩率低
 * Deflater.BEST_COMPRESSION: 最佳压缩，速度慢
 * Deflater.DEFAULT_COMPRESSION: 平衡模式
 */
val compressionLevel = Deflater.DEFAULT_COMPRESSION
```

## 6. 性能调优配置

### 6.1 JVM 参数

```bash
# 堆内存大小
-Xms4g -Xmx4g

# 新生代大小
-Xmn2g

# GC 算法（G1）
-XX:+UseG1GC

# 元空间大小
-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m

# 直接内存大小
-XX:MaxDirectMemorySize=2g

# GC 日志
-Xlog:gc*:file=gc.log:time,tags:filecount=5,filesize=100m
```

### 6.2 操作系统参数

```bash
# 最大文件描述符数
ulimit -n 65535

# TCP 参数
sysctl -w net.core.rmem_max=16777216
sysctl -w net.core.wmem_max=16777216
sysctl -w net.ipv4.tcp_rmem=4096 87380 16777216
sysctl -w net.ipv4.tcp_wmem=4096 65536 16777216

# 关闭 swap
swapoff -a
```

## 7. 配置最佳实践

### 7.1 生产环境配置

```kotlin
val brokerConfig = BrokerConfig(
    brokerName = "ProdBroker",
    brokerId = 0,
    clusterName = "ProductionCluster",
    listenPort = 10911,
    namesrvAddr = "namesrv1:9876;namesrv2:9876",
    storePathRootDir = "/data/kocketmq/store",
    commitLogFileSize = 1024 * 1024 * 1024,  // 1GB
    processThreads = 32,
    autoCreateTopicEnable = false,  // 生产环境禁用自动创建
    defaultTopicQueueNums = 16
)
```

### 7.2 开发环境配置

```kotlin
val brokerConfig = BrokerConfig(
    brokerName = "DevBroker",
    listenPort = 10911,
    storePathRootDir = "/tmp/kocketmq/dev",
    commitLogFileSize = 1024 * 1024 * 100,  // 100MB
    autoCreateTopicEnable = true,
    defaultTopicQueueNums = 4
)
```

### 7.3 测试环境配置

```kotlin
val brokerConfig = BrokerConfig(
    brokerName = "TestBroker",
    listenPort = 10919,
    storePathRootDir = "/tmp/kocketmq/test",
    commitLogFileSize = 1024 * 1024 * 50,  // 50MB
    autoCreateTopicEnable = true
)
```

## 8. 配置管理

### 8.1 配置持久化

Topic 配置和消费者 Offset 会自动持久化到 JSON 文件：

```
{storePathRootDir}/
├── config/
│   ├── topics.json           # Topic 配置
│   └── consumerOffset.json   # 消费者 Offset
└── commitlog/
    └── ...
```

### 8.2 动态更新配置

```kotlin
// 更新 Topic 配置（立即生效）
brokerController.topicConfigManager.updateTopicConfig(newTopicConfig)

// 持久化配置
brokerController.topicConfigManager.persist()
```

---

**文档版本**：v1.0
**最后更新**：2026-03-21
**维护者**：KocketMQ 团队
