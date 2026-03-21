# KocketMQ Broker实施计划

**基于文档：** 2026-03-20-broker-core-design.md
**计划周期：** 12周（5个阶段）
**开始日期：** 2026-03-20

---

## 总览

### 实施策略

**开发原则：**
1. **增量开发** - 每个阶段都能独立测试
2. **垂直切片** - 从底层到上层完整实现
3. **持续集成** - 每个功能都有对应的测试
4. **文档同步** - 代码和文档保持一致

### 技术栈

- **语言：** Kotlin 1.5.31
- **协程：** kotlinx-coroutines
- **构建：** Gradle 7.0.2
- **测试：** JUnit Jupiter 5.5.2
- **网络：** Netty 4.0.42.Final

---

## 阶段1：基础存储引擎（第1-2周）

### 目标
实现CommitLog和ConsumeQueue，确保消息能正确写入和读取。

### 里程碑1.1：文件映射层（第1周前3天）

#### 任务1.1.1：创建broker模块结构
**优先级：** P0
**预估时间：** 2小时
**依赖：** 无

**任务内容：**
1. 在`settings.gradle`中添加broker模块
2. 创建包结构：
   ```
   broker/
   ├── src/main/kotlin/com/agmtopy/kocketmq/broker/
   │   ├── store/
   │   │   ├── MappedFile.kt
   │   │   ├── MappedFileQueue.kt
   │   │   ├── CommitLogActor.kt
   │   │   └── ConsumeQueueBuilderActor.kt
   │   └── config/
   │       └── BrokerConfig.kt
   ```
3. 配置`broker/build.gradle`依赖

**验收标准：**
- [ ] broker模块能成功编译
- [ ] 包结构符合设计文档

**测试：**
```bash
./gradlew :broker:build
```

---

#### 任务1.1.2：实现MappedFile
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务1.1.1

**任务内容：**
1. 实现`MappedFile`类：
   - 构造函数（fileName, fileSize）
   - `appendMessage(ByteBuffer)` - 追加消息
   - `getMessage(offset, size)` - 读取消息
   - `flush(flushLeastPages)` - 刷盘
   - `isFull()` - 判断文件是否满

2. 实现原子计数器：
   - `wrotePosition` - 写入位置
   - `flushedPosition` - 刷盘位置

**代码骨架：**
```kotlin
class MappedFile(
    val fileName: String,
    val fileSize: Int
) {
    private val mappedByteBuffer: MappedByteBuffer
    private val wrotePosition = AtomicInteger(0)
    private val flushedPosition = AtomicInteger(0)

    init {
        // 创建文件并映射到内存
    }

    suspend fun appendMessage(data: ByteBuffer): AppendMessageResult
    fun getMessage(offset: Int, size: Int): ByteBuffer?
    suspend fun flush(flushLeastPages: Int = 0): Boolean
    fun isFull(): Boolean
}
```

**验收标准：**
- [ ] 能成功创建MappedFile
- [ ] 能追加消息到文件
- [ ] 能从文件读取消息
- [ ] 刷盘功能正常

**测试：**
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
    assertEquals("Hello", String(read.array()))
}
```

---

#### 任务1.1.3：实现MappedFileQueue
**优先级：** P0
**预估时间：** 0.5天
**依赖：** 任务1.1.2

**任务内容：**
1. 实现`MappedFileQueue`类：
   - `load()` - 加载所有文件
   - `lastMappedFile()` - 获取最后一个文件
   - `createMappedFile()` - 创建新文件
   - `findMappedFile(offset)` - 根据偏移量查找文件
   - `flush()` - 批量刷盘

**代码骨架：**
```kotlin
class MappedFileQueue(
    private val storePath: String,
    private val mappedFileSize: Int
) {
    private val mappedFiles = mutableListOf<MappedFile>()

    suspend fun load(): Boolean
    fun lastMappedFile(): MappedFile?
    suspend fun createMappedFile(): MappedFile?
    fun findMappedFile(offset: Long): MappedFile?
    suspend fun flush(flushLeastPages: Int): Boolean
    fun maxOffset(): Long
    fun minOffset(): Long
}
```

**验收标准：**
- [ ] 能加载现有文件
- [ ] 能创建新文件
- [ ] 能根据偏移量定位文件

**测试：**
```kotlin
@Test
fun `test create and find mapped file`() = runBlocking {
    val queue = MappedFileQueue("/tmp/queue", 1024)

    // 创建文件
    val file1 = queue.createMappedFile()
    assertNotNull(file1)

    // 写入数据
    file1.appendMessage(ByteBuffer.wrap("test".toByteArray()))

    // 查找文件
    val found = queue.findMappedFile(0)
    assertNotNull(found)
    assertEquals(file1, found)
}
```

---

### 里程碑1.2：消息编码层（第1周后2天）

#### 任务1.2.1：定义消息数据结构
**优先级：** P0
**预估时间：** 0.5天
**依赖：** 任务1.1.1

**任务内容：**
1. 在common模块创建`MessageExt`类：
   ```kotlin
   data class MessageExt(
       val topic: String,
       var queueId: Int = 0,
       var body: ByteArray,
       var properties: String? = null,
       var flag: Int = 0,
       var sysFlag: Int = 0,
       var bodyCRC: Int = 0,
       var queueOffset: Long = 0L,
       var bornTimestamp: Long = 0L,
       var storeTimestamp: Long = 0L
   )
   ```

2. 创建消息编解码工具：
   - `encodeMessage(MessageExt): ByteBuffer`
   - `decodeMessage(ByteBuffer): MessageExt?`
   - `calculateCRC32(ByteArray): Int`

**验收标准：**
- [ ] 消息能正确编码为ByteBuffer
- [ ] ByteBuffer能正确解码为MessageExt
- [ ] CRC32校验正常

**测试：**
```kotlin
@Test
fun `test message encode and decode`() {
    val message = MessageExt(
        topic = "TestTopic",
        queueId = 0,
        body = "Hello, KocketMQ!".toByteArray(),
        properties = "TAGS=TagA"
    )

    val buffer = encodeMessage(message)
    val decoded = decodeMessage(buffer)

    assertNotNull(decoded)
    assertEquals("TestTopic", decoded?.topic)
    assertEquals("Hello, KocketMQ!", String(decoded?.body ?: ByteArray(0)))
}
```

---

### 里程碑1.3：CommitLog Actor（第2周前3天）

#### 任务1.3.1：实现CommitLogActor
**优先级：** P0
**预估时间：** 2天
**依赖：** 任务1.1.3, 任务1.2.1

**任务内容：**
1. 实现`CommitLogActor`类：
   - 使用Channel接收请求
   - 串行处理写入请求
   - 编码消息并写入MappedFile
   - 更新maxOffset

2. 实现消息读取：
   - 根据物理偏移量定位文件
   - 解码消息

**代码骨架：**
```kotlin
class CommitLogActor(
    private val storePath: String,
    private val mappedFileSize: Int = 1024 * 1024 * 1024
) {
    private val requestChannel = Channel<AppendRequest>(Channel.UNLIMITED)
    private val mappedFileQueue = MappedFileQueue(...)

    private val _maxOffset = MutableStateFlow(0L)

    suspend fun load(): Boolean
    fun start()
    suspend fun appendMessage(messageExt: MessageExt): AppendMessageResult
    suspend fun getMessage(phyOffset: Long): MessageExt?
    suspend fun flush(flushLeastPages: Int = 0): Boolean
}
```

**验收标准：**
- [ ] 能并发接收请求，串行处理
- [ ] 消息能正确写入CommitLog
- [ ] 能根据偏移量读取消息
- [ ] 文件满时自动创建新文件

**测试：**
```kotlin
@Test
fun `test concurrent append`() = runBlocking {
    val commitLog = CommitLogActor("/tmp/commitlog")
    assertTrue(commitLog.load())
    commitLog.start()

    // 并发写入100条消息
    val jobs = (1..100).map { i ->
        GlobalScope.async {
            val msg = createTestMessage(i)
            commitLog.appendMessage(msg)
        }
    }

    val results = jobs.awaitAll()
    assertTrue(results.all { it.status == AppendMessageStatus.PUT_OK })
}
```

---

### 里程碑1.4：ConsumeQueue Actor（第2周后2天）

#### 任务1.4.1：实现ConsumeQueueBuilderActor
**优先级：** P0
**预估时间：** 1.5天
**依赖：** 任务1.3.1

**任务内容：**
1. 实现`ConsumeQueueBuilderActor`类：
   - 管理每个Topic-QueueId的ConsumeQueue
   - 构建索引单元（20字节）
   - 提供offset查询接口

2. 实现`ConsumeQueue`类：
   - 管理单个队列的索引文件
   - 写入索引单元
   - 读取索引单元

**代码骨架：**
```kotlin
class ConsumeQueueBuilderActor(
    private val storePath: String,
    private val mappedFileSize: Int = 1024 * 1024 * 6
) {
    private val consumeQueueTable = ConcurrentHashMap<String, ConsumeQueue>()

    suspend fun buildConsumeQueue(
        topic: String,
        queueId: Int,
        phyOffset: Long,
        size: Int,
        tagsCode: Long = 0
    )

    suspend fun getPhysicOffsets(
        topic: String,
        queueId: Int,
        startOffset: Long,
        maxNums: Int
    ): List<Long>
}

class ConsumeQueue(
    private val storePath: String,
    private val topic: String,
    private val queueId: Int,
    private val mappedFileSize: Int
) {
    suspend fun append(phyOffset: Long, size: Int, tagsCode: Long)
    fun getIndex(logicOffset: Long): Long
}
```

**验收标准：**
- [ ] 能构建ConsumeQueue索引
- [ ] 能根据逻辑offset查询物理offset
- [ ] 索引单元格式正确（20字节）

**测试：**
```kotlin
@Test
fun `test consume queue build and query`() = runBlocking {
    val builder = ConsumeQueueBuilderActor("/tmp/store")

    // 构建索引
    builder.buildConsumeQueue("TestTopic", 0, 100L, 50, 0)
    builder.buildConsumeQueue("TestTopic", 0, 200L, 60, 0)

    // 查询索引
    val offsets = builder.getPhysicOffsets("TestTopic", 0, 0, 10)

    assertEquals(2, offsets.size)
    assertEquals(100L, offsets[0])
    assertEquals(200L, offsets[1])
}
```

---

### 里程碑1.5：集成测试（第2周最后1天）

#### 任务1.5.1：MessageStoreActor集成
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务1.3.1, 任务1.4.1

**任务内容：**
1. 实现`MessageStoreActor`：
   - 协调CommitLogActor和ConsumeQueueBuilderActor
   - 提供`putMessage()`接口
   - 提供`getMessage()`接口

2. 编写端到端测试

**代码骨架：**
```kotlin
class MessageStoreActor(
    private val storePath: String,
    private val config: StoreConfig
) {
    private lateinit var commitLog: CommitLogActor
    private lateinit var consumeQueueBuilder: ConsumeQueueBuilderActor

    suspend fun load(): Boolean
    fun start()
    suspend fun putMessage(message: MessageExt): PutMessageResult
    suspend fun getMessage(topic: String, queueId: Int, offset: Long, maxMsgNums: Int): List<MessageExt>
}
```

**验收标准：**
- [ ] 消息能正确存储
- [ ] 消息能正确读取
- [ ] ConsumeQueue与CommitLog一致

**性能测试：**
```kotlin
@Test
fun `test store throughput`() = runBlocking {
    val store = MessageStoreActor("/tmp/store", StoreConfig())
    store.load()
    store.start()

    val messageCount = 10_000
    val startTime = System.currentTimeMillis()

    // 并发写入
    val jobs = (1..messageCount).map { i ->
        GlobalScope.async(Dispatchers.IO) {
            store.putMessage(createTestMessage(i))
        }
    }

    jobs.awaitAll()

    val costTime = System.currentTimeMillis() - startTime
    val tps = messageCount * 1000 / costTime

    println("TPS: $tps messages/sec")
    assertTrue(tps > 5_000)  // 目标TPS > 5000（单协程）
}
```

---

## 阶段2：Broker核心（第3-5周）

### 目标
实现Broker启动流程、请求处理和基本的管理功能。

### 里程碑2.1：配置和启动（第3周）

#### 任务2.1.1：实现BrokerConfig
**优先级：** P0
**预估时间：** 0.5天
**依赖：** 无

**任务内容：**
1. 创建`BrokerConfig`数据类：
   ```kotlin
   data class BrokerConfig(
       val brokerName: String = "DefaultBroker",
       val brokerId: Long = 0,
       val clusterName: String = "DefaultCluster",
       val listenPort: Int = 10911,
       val storePathRootDir: String = "/tmp/kocketmq/store",
       // ... 其他配置
   )
   ```

2. 支持从配置文件加载

**验收标准：**
- [ ] 能创建默认配置
- [ ] 能从JSON文件加载配置

---

#### 任务2.1.2：实现BrokerController
**优先级：** P0
**预估时间：** 3天
**依赖：** 任务1.5.1, 任务2.1.1

**任务内容：**
1. 实现状态机（BrokerState）
2. 实现`initialize()`方法：
   - 初始化MessageStoreActor
   - 初始化TopicManagerActor
   - 初始化ConsumerManagerActor
   - 初始化RequestDispatcherActor
   - 初始化RemotingServer

3. 实现`start()`方法：
   - 启动所有Actor
   - 启动Netty Server
   - 注册到NameServer（可选）

4. 实现`shutdown()`方法：
   - 优雅关闭流程

**代码骨架：**
```kotlin
class BrokerController(
    private val brokerConfig: BrokerConfig,
    private val nettyServerConfig: NettyServerConfig,
    private val nameServerAddresses: List<String>
) {
    private val _state = MutableStateFlow<BrokerState>(BrokerState.NEW)

    suspend fun initialize(): Boolean
    suspend fun start(): Boolean
    suspend fun shutdown()
}
```

**验收标准：**
- [ ] Broker能成功启动
- [ ] 所有组件初始化正确
- [ ] 状态转换正确
- [ ] 能优雅关闭

---

#### 任务2.1.3：实现BrokerStartup
**优先级：** P0
**预估时间：** 0.5天
**依赖：** 任务2.1.2

**任务内容：**
1. 实现主入口：
   ```kotlin
   suspend fun main(args: Array<String>) {
       val brokerConfig = parseArgs(args)
       val controller = BrokerController(...)

       if (!controller.initialize()) {
           exitProcess(-1)
       }

       if (!controller.start()) {
           exitProcess(-1)
       }

       // 等待关闭
       controller.state.collect { ... }
   }
   ```

**验收标准：**
- [ ] 能通过命令行启动Broker
- [ ] 能正确解析参数

---

### 里程碑2.2：请求处理（第4周）

#### 任务2.2.1：实现RequestDispatcherActor
**优先级：** P0
**预估时间：** 2天
**依赖：** 任务2.1.2

**任务内容：**
1. 实现请求分发逻辑
2. 实现背压控制（有界Channel）
3. 实现Processor注册机制
4. 实现请求统计

**验收标准：**
- [ ] 能接收并分发请求
- [ ] 背压控制正常
- [ ] 能统计请求数量

---

#### 任务2.2.2：实现BrokerRequestProcessor
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务2.2.1

**任务内容：**
1. 实现Netty到协程的桥接：
   - `CoroutineProcessorAdapter`
   - 使用`runBlocking`桥接

2. 测试桥接功能

**验收标准：**
- [ ] 能将Netty请求转发到Actor
- [ ] 能返回Actor的响应

---

#### 任务2.2.3：实现SendMessageProcessor
**优先级：** P0
**预估时间：** 2天
**依赖：** 任务2.2.2

**任务内容：**
1. 解析发送消息请求
2. 检查Topic是否存在
3. 分配QueueId
4. 调用MessageStore存储
5. 构建响应

**验收标准：**
- [ ] 能处理SEND_MESSAGE请求
- [ ] 消息能正确存储
- [ ] 返回正确的响应

---

#### 任务2.2.4：实现PullMessageProcessor
**优先级：** P0
**预估时间：** 2天
**依赖：** 任务2.2.2

**任务内容：**
1. 解析拉取消息请求
2. 查询Offset
3. 从MessageStore拉取消息
4. 构建响应

**验收标准：**
- [ ] 能处理PULL_MESSAGE请求
- [ ] 能正确拉取消息
- [ ] 返回正确的响应

---

### 里程碑2.3：管理功能（第5周）

#### 任务2.3.1：实现TopicManagerActor
**优先级：** P0
**预估时间：** 2天
**依赖：** 任务2.1.2

**任务内容：**
1. 管理Topic配置表
2. 实现自动创建Topic
3. 实现Queue分配（Round-Robin）
4. 实现Topic持久化

**验收标准：**
- [ ] 能创建和管理Topic
- [ ] 能自动创建Topic
- [ ] Queue分配正确

---

#### 任务2.3.2：实现ConsumerManagerActor
**优先级：** P0
**预估时间：** 2天
**依赖：** 任务2.1.2

**任务内容：**
1. 管理消费者组
2. 处理消费者注册（HEART_BEAT）
3. 实现Offset管理
4. 清理过期消费者

**验收标准：**
- [ ] 能注册消费者
- [ ] 能管理Offset
- [ ] 能清理过期消费者

---

#### 任务2.3.3：实现OffsetManager
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务2.3.2

**任务内容：**
1. 维护Offset表
2. 持久化Offset到文件
3. 加载Offset

**验收标准：**
- [ ] 能查询和更新Offset
- [ ] 能持久化Offset
- [ ] 能加载Offset

---

#### 任务2.3.4：实现NameServer注册
**优先级：** P1
**预估时间：** 1天
**依赖：** 任务2.1.2

**任务内容：**
1. 实现注册逻辑
2. 实现心跳发送
3. 实现注销逻辑

**验收标准：**
- [ ] 能注册到NameServer
- [ ] 心跳正常发送
- [ ] 能从NameServer注销

---

## 阶段3：Producer客户端（第6-7周）

### 目标
实现Producer客户端，能发送消息到Broker。

### 里程碑3.1：客户端基础（第6周）

#### 任务3.1.1：创建client模块
**优先级：** P0
**预估时间：** 0.5天
**依赖：** 无

**任务内容：**
1. 在`settings.gradle`添加client模块
2. 创建包结构：
   ```
   client/
   ├── src/main/kotlin/com/agmtopy/kocketmq/client/
   │   ├── producer/
   │   │   ├── DefaultMQProducer.kt
   │   │   └── ProducerActor.kt
   │   ├── impl/
   │   │   └── NameServerClient.kt
   │   └── config/
   │       └── ClientConfig.kt
   ```

---

#### 任务3.1.2：实现ClientConfig
**优先级：** P0
**预估时间：** 0.5天
**依赖：** 任务3.1.1

**任务内容：**
1. 创建`ClientConfig`数据类
2. 配置NameServer地址、超时等

---

#### 任务3.1.3：实现NameServerClient
**优先级：** P0
**预估时间：** 2天
**依赖：** 任务3.1.2

**任务内容：**
1. 实现路由查询
2. 实现路由缓存
3. 实现缓存过期检查

**验收标准：**
- [ ] 能从NameServer查询路由
- [ ] 路由缓存正常工作

---

### 里程碑3.2：Producer实现（第7周）

#### 任务3.2.1：实现ProducerActor
**优先级：** P0
**预估时间：** 3天
**依赖：** 任务3.1.3

**任务内容：**
1. 实现消息发送逻辑
2. 实现路由选择
3. 实现重试机制
4. 实现批量发送

**验收标准：**
- [ ] 能发送消息
- [ ] 重试机制正常
- [ ] 能批量发送

---

#### 任务3.2.2：实现DefaultMQProducer API
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务3.2.1

**任务内容：**
1. 实现`send()`方法
2. 实现`sendAsync()`方法
3. 实现`sendOneway()`方法
4. 实现`sendBatch()`方法

**验收标准：**
- [ ] API易用性好
- [ ] 所有发送模式正常

---

#### 任务3.2.3：Producer测试
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务3.2.2

**任务内容：**
1. 单元测试
2. 集成测试（连接真实Broker）
3. 性能测试

**验收标准：**
- [ ] 测试覆盖率 > 80%
- [ ] 能发送消息到Broker
- [ ] TPS达到预期

---

## 阶段4：Consumer客户端（第8-10周）

### 目标
实现Consumer客户端，能拉取和消费消息。

### 里程碑4.1：Consumer基础（第8周）

#### 任务4.1.1：实现OffsetStore
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务3.1.1

**任务内容：**
1. 实现`LocalOffsetStore`（本地文件）
2. 实现`RemoteOffsetStore`（Broker端）

**验收标准：**
- [ ] 能持久化Offset
- [ ] 能加载Offset

---

#### 任务4.1.2：实现ConsumerActor
**优先级：** P0
**预估时间：** 3天
**依赖：** 任务4.1.1

**任务内容：**
1. 实现消息拉取逻辑
2. 实现消息处理
3. 实现Offset更新

**验收标准：**
- [ ] 能拉取消息
- [ ] 能处理消息
- [ ] Offset正确更新

---

### 里程碑4.2：Consumer完整实现（第9周）

#### 任务4.2.1：实现RebalanceActor
**优先级：** P0
**预估时间：** 3天
**依赖：** 任务4.1.2

**任务内容：**
1. 实现消费者发现
2. 实现Queue分配算法
3. 实现定时重平衡

**验收标准：**
- [ ] 能发现所有消费者
- [ ] Queue分配正确
- [ ] 重平衡正常

---

#### 任务4.2.2：实现DefaultMQPushConsumer API
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务4.2.1

**任务内容：**
1. 实现`subscribe()`方法
2. 实现`setMessageListener()`方法
3. 实现`start()`方法

**验收标准：**
- [ ] API易用性好
- [ ] 能消费消息

---

#### 任务4.2.3：Consumer测试
**优先级：** P0
**预估时间：** 1天
**依赖：** 任务4.2.2

**任务内容：**
1. 单元测试
2. 集成测试
3. 重平衡测试

**验收标准：**
- [ ] 测试覆盖率 > 80%
- [ ] 能消费消息
- [ ] 重平衡正常

---

## 阶段5：测试和优化（第11-12周）

### 目标
完整测试、性能优化和文档编写。

### 里程碑5.1：端到端测试（第11周）

#### 任务5.1.1：集成测试套件
**优先级：** P0
**预估时间：** 3天
**依赖：** 阶段4完成

**任务内容：**
1. NameServer + Broker + Producer + Consumer完整流程测试
2. 故障恢复测试
3. 边界条件测试

**验收标准：**
- [ ] 端到端测试通过
- [ ] 故障恢复测试通过

---

#### 任务5.1.2：性能测试
**优先级：** P0
**预估时间：** 2天
**依赖：** 任务5.1.1

**任务内容：**
1. TPS测试
2. 延迟测试
3. 稳定性测试（长时间运行）

**验收标准：**
- [ ] TPS > 10万/秒
- [ ] P99延迟 < 10ms
- [ ] 稳定运行24小时无错误

---

### 里程碑5.2：优化和文档（第12周）

#### 任务5.2.1：性能优化
**优先级：** P1
**预估时间：** 3天
**依赖：** 任务5.1.2

**任务内容：**
1. 分析性能瓶颈
2. 优化热点代码
3. 优化内存使用

**验收标准：**
- [ ] 性能达标
- [ ] 内存使用合理

---

#### 任务5.2.2：文档编写
**优先级：** P1
**预估时间：** 2天
**依赖：** 任务5.1.2

**任务内容：**
1. 用户文档
2. API文档
3. 架构文档

**验收标准：**
- [ ] 文档完整清晰
- [ ] 示例代码可运行

---

## 风险和依赖

### 技术风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 协程性能不达预期 | 高 | 提前做性能验证 |
| Netty集成复杂 | 中 | 使用适配器模式隔离 |
| 协议兼容性问题 | 高 | 对比测试RocketMQ客户端 |

### 外部依赖

| 依赖 | 状态 | 备注 |
|------|------|------|
| Netty 4.0.42 | ✅ 已有 | remoting模块已集成 |
| Kotlin协程 | ✅ 已有 | 项目已配置 |
| JUnit 5 | ✅ 已有 | 测试框架已配置 |

---

## 验收标准

### 功能验收

- [ ] Broker能独立启动和关闭
- [ ] Producer能发送消息
- [ ] Consumer能消费消息
- [ ] Offset正确管理
- [ ] 能注册到NameServer

### 性能验收

- [ ] TPS > 10万/秒
- [ ] 发送延迟P99 < 10ms
- [ ] 拉取延迟P99 < 5ms
- [ ] 内存占用 < 4GB

### 质量验收

- [ ] 测试覆盖率 > 80%
- [ ] 所有测试通过
- [ ] 无严重Bug
- [ ] 文档完整

---

## 进度跟踪

### 每周检查点

**第1周：**
- [ ] MappedFile完成
- [ ] MappedFileQueue完成
- [ ] 消息编码完成

**第2周：**
- [ ] CommitLogActor完成
- [ ] ConsumeQueueBuilderActor完成
- [ ] MessageStoreActor集成测试通过

**第3周：**
- [ ] BrokerConfig完成
- [ ] BrokerController启动流程完成
- [ ] BrokerStartup完成

**第4周：**
- [ ] RequestDispatcherActor完成
- [ ] SendMessageProcessor完成
- [ ] PullMessageProcessor完成

**第5周：**
- [ ] TopicManagerActor完成
- [ ] ConsumerManagerActor完成
- [ ] OffsetManager完成

**第6周：**
- [ ] client模块创建
- [ ] NameServerClient完成

**第7周：**
- [ ] ProducerActor完成
- [ ] DefaultMQProducer API完成
- [ ] Producer测试通过

**第8周：**
- [ ] OffsetStore完成
- [ ] ConsumerActor完成

**第9周：**
- [ ] RebalanceActor完成
- [ ] DefaultMQPushConsumer API完成

**第10周：**
- [ ] Consumer测试通过
- [ ] 所有客户端功能完成

**第11周：**
- [ ] 端到端测试通过
- [ ] 性能测试通过

**第12周：**
- [ ] 性能优化完成
- [ ] 文档编写完成
- [ ] 项目验收

---

## 附录：任务依赖图

```
阶段1: 基础存储
├─ 1.1.1 创建broker模块
│   ├─ 1.1.2 MappedFile
│   │   └─ 1.1.3 MappedFileQueue
│   │       └─ 1.3.1 CommitLogActor
│   │           └─ 1.5.1 MessageStoreActor
│   └─ 1.2.1 消息数据结构
│       └─ 1.3.1 CommitLogActor
│           └─ 1.4.1 ConsumeQueueBuilderActor
│               └─ 1.5.1 MessageStoreActor

阶段2: Broker核心
├─ 2.1.1 BrokerConfig
│   └─ 2.1.2 BrokerController
│       ├─ 2.1.3 BrokerStartup
│       ├─ 2.2.1 RequestDispatcherActor
│       │   └─ 2.2.2 BrokerRequestProcessor
│       │       ├─ 2.2.3 SendMessageProcessor
│       │       └─ 2.2.4 PullMessageProcessor
│       ├─ 2.3.1 TopicManagerActor
│       └─ 2.3.2 ConsumerManagerActor
│           └─ 2.3.3 OffsetManager

阶段3: Producer客户端
├─ 3.1.1 创建client模块
│   └─ 3.1.2 ClientConfig
│       └─ 3.1.3 NameServerClient
│           └─ 3.2.1 ProducerActor
│               └─ 3.2.2 DefaultMQProducer API
│                   └─ 3.2.3 Producer测试

阶段4: Consumer客户端
└─ 4.1.1 OffsetStore
    └─ 4.1.2 ConsumerActor
        └─ 4.2.1 RebalanceActor
            └─ 4.2.2 DefaultMQPushConsumer API
                └─ 4.2.3 Consumer测试

阶段5: 测试和优化
└─ 5.1.1 集成测试套件
    └─ 5.1.2 性能测试
        ├─ 5.2.1 性能优化
        └─ 5.2.2 文档编写
```

---

**文档版本：** 1.0
**最后更新：** 2026-03-20
