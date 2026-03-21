# KocketMQ API 使用指南

## 1. Broker API

### 1.1 启动 Broker

```kotlin
import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.config.BrokerConfig

fun main() = runBlocking {
    // 创建配置
    val brokerConfig = BrokerConfig(
        brokerName = "DefaultBroker",
        brokerId = 0,
        clusterName = "DefaultCluster",
        listenPort = 10911,
        storePathRootDir = "/tmp/kocketmq/store",
        autoCreateTopicEnable = true,
        defaultTopicQueueNums = 8
    )

    // 创建 Broker 控制器
    val brokerController = BrokerController(brokerConfig)

    // 初始化
    val initialized = brokerController.initialize()
    if (!initialized) {
        println("Broker 初始化失败")
        return@runBlocking
    }

    // 启动
    brokerController.start()
    println("Broker 已启动，监听端口: ${brokerConfig.listenPort}")

    // 等待关闭信号
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            brokerController.shutdown()
            println("Broker 已关闭")
        }
    })

    // 保持运行
    Thread.sleep(Long.MAX_VALUE)
}
```

### 1.2 发送消息

```kotlin
import com.agmtopy.kocketmq.broker.store.MessageExt

suspend fun sendMessage(brokerController: BrokerController) {
    // 创建消息
    val message = MessageExt(
        topic = "TestTopic",
        queueId = 0,
        body = "Hello KocketMQ".toByteArray(),
        bornTimestamp = System.currentTimeMillis(),
        flag = 0,
        sysFlag = 0,
        preparedTransactionOffset = 0
    )

    // 发送消息
    val result = brokerController.messageStore.putMessage(message)

    println("消息发送成功:")
    println("  msgId: ${result.msgId}")
    println("  topic: ${result.topic}")
    println("  queueId: ${result.queueId}")
    println("  queueOffset: ${result.queueOffset}")
}
```

### 1.3 拉取消息

```kotlin
suspend fun pullMessage(brokerController: BrokerController) {
    val topic = "TestTopic"
    val queueId = 0
    val offset = 0L
    val maxNums = 32

    // 批量拉取消息
    val messages = brokerController.messageStore.getMessages(
        topic = topic,
        queueId = queueId,
        startLogicOffset = offset,
        maxNums = maxNums
    )

    println("拉取到 ${messages.size} 条消息")

    for (message in messages) {
        println("  offset=${message.queueOffset}, body=${String(message.body)}")
    }
}
```

### 1.4 查询单条消息

```kotlin
import com.agmtopy.kocketmq.broker.store.GetMessageStatus

suspend fun getMessage(brokerController: BrokerController) {
    val topic = "TestTopic"
    val queueId = 0
    val logicOffset = 0L

    // 查询单条消息
    val result = brokerController.messageStore.getMessage(
        topic = topic,
        queueId = queueId,
        logicOffset = logicOffset
    )

    when (result.status) {
        GetMessageStatus.GET_OK -> {
            println("消息查询成功:")
            println("  body=${String(result.message!!.body)}")
        }
        GetMessageStatus.GET_NOT_FOUND -> {
            println("消息不存在")
        }
        else -> {
            println("查询失败: ${result.status}")
        }
    }
}
```

## 2. Topic 管理 API

### 2.1 创建 Topic

```kotlin
import com.agmtopy.kocketmq.broker.topic.TopicConfig
import com.agmtopy.kocketmq.common.topic.TopicFilterType

suspend fun createTopic(brokerController: BrokerController) {
    val topicConfig = TopicConfig(
        topicName = "NewTopic",
        readQueueNums = 8,
        writeQueueNums = 8,
        perm = 6,  // 可读可写
        topicFilterType = TopicFilterType.SINGLE_TAG,
        order = false
    )

    brokerController.topicConfigManager.updateTopicConfig(topicConfig)
    println("Topic 创建成功: ${topicConfig.topicName}")
}
```

### 2.2 查询 Topic 配置

```kotlin
fun queryTopicConfig(brokerController: BrokerController) {
    val topicName = "TestTopic"

    val topicConfig = brokerController.topicConfigManager.getTopicConfig(topicName)

    if (topicConfig != null) {
        println("Topic 配置:")
        println("  topicName: ${topicConfig.topicName}")
        println("  readQueueNums: ${topicConfig.readQueueNums}")
        println("  writeQueueNums: ${topicConfig.writeQueueNums}")
        println("  perm: ${topicConfig.perm}")
    } else {
        println("Topic 不存在")
    }
}
```

### 2.3 列出所有 Topic

```kotlin
fun listTopics(brokerController: BrokerController) {
    val topicConfigs = brokerController.topicConfigManager.getTopicConfigTable()

    println("所有 Topic (${topicConfigs.size} 个):")
    for ((topicName, config) in topicConfigs) {
        println("  $topicName: readQueues=${config.readQueueNums}, writeQueues=${config.writeQueueNums}")
    }
}
```

### 2.4 删除 Topic

```kotlin
fun deleteTopic(brokerController: BrokerController) {
    val topicName = "OldTopic"

    brokerController.topicConfigManager.deleteTopicConfig(topicName)
    println("Topic 已删除: $topicName")
}
```

## 3. 消费者 Offset 管理 API

### 3.1 提交 Offset

```kotlin
suspend fun commitOffset(brokerController: BrokerController) {
    val group = "TestGroup"
    val topic = "TestTopic"
    val queueId = 0
    val offset = 100L

    brokerController.consumerOffsetManager.commitOffset(
        group = group,
        topic = topic,
        queueId = queueId,
        offset = offset
    )

    println("Offset 已提交: group=$group, topic=$topic, queueId=$queueId, offset=$offset")
}
```

### 3.2 查询 Offset

```kotlin
fun queryOffset(brokerController: BrokerController) {
    val group = "TestGroup"
    val topic = "TestTopic"
    val queueId = 0

    val offset = brokerController.consumerOffsetManager.queryOffset(
        group = group,
        topic = topic,
        queueId = queueId
    )

    println("当前 Offset: $offset")
}
```

### 3.3 查询所有 Offset

```kotlin
fun queryAllOffsets(brokerController: BrokerController) {
    val group = "TestGroup"

    val offsetTable = brokerController.consumerOffsetManager.queryAllOffset(group)

    println("Group $group 的所有 Offset:")
    for ((topic, queueOffsets) in offsetTable) {
        for ((queueId, offset) in queueOffsets) {
            println("  $topic:$queueId = $offset")
        }
    }
}
```

## 4. 统计监控 API

### 4.1 查询统计信息

```kotlin
fun queryStats(brokerController: BrokerController) {
    val stats = brokerController.brokerStats

    println("========== Broker 统计信息 ==========")
    println("发送消息总数: ${stats.sendmessageNums.get()}")
    println("发送失败总数: ${stats.sendmessageFailedNums.get()}")
    println("拉取消息总数: ${stats.pullMessageNums.get()}")
    println("拉取失败总数: ${stats.pullMessageFailedNums.get()}")
    println("发送 TPS: ${"%.2f".format(stats.getSendTPS())}")
    println("拉取 TPS: ${"%.2f".format(stats.getPullTPS())}")
    println("=====================================")
}
```

## 5. 批量消息 API

### 5.1 批量发送消息

```kotlin
suspend fun sendBatchMessages(brokerController: BrokerController) {
    val messages = List(100) { index ->
        MessageExt(
            topic = "BatchTopic",
            queueId = 0,
            body = "Batch message $index".toByteArray(),
            bornTimestamp = System.currentTimeMillis()
        )
    }

    // 一次性发送批量消息
    for (message in messages) {
        brokerController.messageStore.putMessage(message)
    }

    println("批量发送了 ${messages.size} 条消息")
}
```

## 6. 消息压缩 API

### 6.1 自动压缩

```kotlin
import com.agmtopy.kocketmq.broker.compress.MessageCompressor

suspend fun sendCompressedMessage(brokerController: BrokerController) {
    // 创建大消息（超过压缩阈值）
    val largeBody = ByteArray(1024 * 10) { 'A'.code.toByte() }  // 10KB

    val message = MessageExt(
        topic = "CompressedTopic",
        queueId = 0,
        body = largeBody,
        bornTimestamp = System.currentTimeMillis()
    )

    // 发送时会自动压缩
    val result = brokerController.messageStore.putMessage(message)

    println("压缩消息发送成功: msgId=${result.msgId}")
}
```

### 6.2 手动压缩

```kotlin
fun manualCompress() {
    val originalData = "Hello World".toByteArray()

    // 压缩
    val compressed = MessageCompressor.compress(originalData)
    println("压缩后大小: ${compressed.size}")

    // 解压
    val decompressed = MessageCompressor.decompress(compressed)
    println("解压后数据: ${String(decompressed)}")
}
```

## 7. 完整示例

### 7.1 生产者示例

```kotlin
import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.broker.store.MessageExt
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 启动 Broker
    val brokerConfig = BrokerConfig(
        brokerName = "TestBroker",
        listenPort = 10911,
        storePathRootDir = "/tmp/kocketmq/test",
        autoCreateTopicEnable = true
    )

    val broker = BrokerController(brokerConfig)
    broker.initialize()
    broker.start()

    // 创建 Topic
    val topic = "TestTopic"
    broker.topicConfigManager.createTopic(topic, 4, 4)

    // 发送 100 条消息
    repeat(100) { index ->
        val message = MessageExt(
            topic = topic,
            queueId = index % 4,
            body = "Message $index".toByteArray(),
            bornTimestamp = System.currentTimeMillis()
        )

        val result = broker.messageStore.putMessage(message)
        println("Sent: ${result.msgId}, offset=${result.queueOffset}")
    }

    // 关闭 Broker
    broker.shutdown()
}
```

### 7.2 消费者示例

```kotlin
import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.broker.config.BrokerConfig
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 启动 Broker（连接到已运行的 Broker）
    val brokerConfig = BrokerConfig(
        brokerName = "TestBroker",
        listenPort = 10911,
        storePathRootDir = "/tmp/kocketmq/test"
    )

    val broker = BrokerController(brokerConfig)
    broker.initialize()
    broker.start()

    // 消费消息
    val topic = "TestTopic"
    val queueId = 0
    var offset = 0L
    val group = "TestGroup"

    while (true) {
        // 批量拉取消息
        val messages = broker.messageStore.getMessages(
            topic = topic,
            queueId = queueId,
            startLogicOffset = offset,
            maxNums = 32
        )

        if (messages.isEmpty()) {
            println("没有新消息，等待...")
            Thread.sleep(1000)
            continue
        }

        // 处理消息
        for (message in messages) {
            println("Received: ${String(message.body)}")
            offset++
        }

        // 提交 Offset
        broker.consumerOffsetManager.commitOffset(group, topic, queueId, offset)

        // 达到 100 条后退出
        if (offset >= 100) break
    }

    // 关闭 Broker
    broker.shutdown()
}
```

## 8. 错误处理

### 8.1 发送失败处理

```kotlin
import com.agmtopy.kocketmq.broker.store.PutMessageStatus

suspend fun sendMessageWithErrorHandling(brokerController: BrokerController) {
    val message = MessageExt(
        topic = "TestTopic",
        queueId = 0,
        body = "Test message".toByteArray(),
        bornTimestamp = System.currentTimeMillis()
    )

    val result = brokerController.messageStore.putMessage(message)

    when (result.status) {
        PutMessageStatus.PUT_OK -> {
            println("发送成功")
        }
        PutMessageStatus.PUT_NO_MEMORY -> {
            println("内存不足")
        }
        PutMessageStatus.PUT_MESSAGE_ERROR -> {
            println("消息错误")
        }
        else -> {
            println("未知错误: ${result.status}")
        }
    }
}
```

### 8.2 拉取失败处理

```kotlin
import com.agmtopy.kocketmq.broker.store.GetMessageStatus

suspend fun pullMessageWithErrorHandling(brokerController: BrokerController) {
    val result = brokerController.messageStore.getMessage(
        topic = "TestTopic",
        queueId = 0,
        logicOffset = 1000
    )

    when (result.status) {
        GetMessageStatus.GET_OK -> {
            println("拉取成功: ${String(result.message!!.body)}")
        }
        GetMessageStatus.GET_NOT_FOUND -> {
            println("消息不存在")
        }
        GetMessageStatus.GET_COMMITLOG_ERROR -> {
            println("CommitLog 读取错误")
        }
        else -> {
            println("未知错误: ${result.status}")
        }
    }
}
```

## 9. 最佳实践

### 9.1 Topic 设计

- **队列数量**：根据并发度设置，一般为 CPU 核心数的 2-4 倍
- **Topic 命名**：使用有意义的名称，如 `OrderCreatedEvent`
- **队列选择**：根据业务逻辑选择合适的队列 ID

### 9.2 消息发送

- **批量发送**：尽可能使用批量发送提高吞吐量
- **异步发送**：非关键路径使用异步发送
- **消息大小**：避免过大的消息，考虑使用压缩

### 9.3 消息拉取

- **批量拉取**：一次拉取多条消息提高效率
- **Offset 管理**：及时提交 Offset，避免重复消费
- **错误重试**：实现合理的重试机制

### 9.4 性能优化

- **预热文件**：启动时预热 CommitLog 文件
- **调整配置**：根据硬件调整 CommitLog 文件大小
- **监控指标**：定期检查 Broker 统计信息

---

**文档版本**：v1.0
**最后更新**：2026-03-21
**维护者**：KocketMQ 团队
