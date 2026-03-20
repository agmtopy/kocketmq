# KocketMQ Broker - 快速开始指南

**目标：** 开始实施阶段1 - 基础存储引擎

---

## 第一步：创建broker模块

**任务：** 1.1.1 创建broker模块结构
**时间：** 2小时
**优先级：** P0

### 操作步骤

#### 1. 更新settings.gradle

编辑 `/mnt/d/project/kocketmq/settings.gradle`：

```gradle
rootProject.name = 'kocketmq'
include 'namesrv'
include 'logging'
include 'common'
include 'remoting'
include 'broker'      // 新增
include 'client'      // 新增（后续）
```

#### 2. 创建broker模块目录

```bash
mkdir -p broker/src/main/kotlin/com/agmtopy/kocketmq/broker/store
mkdir -p broker/src/main/kotlin/com/agmtopy/kocketmq/broker/config
mkdir -p broker/src/test/kotlin/com/agmtopy/kocketmq/broker/store
```

#### 3. 创建broker/build.gradle

```gradle
dependencies {
    implementation project(':common')
    implementation project(':remoting')
    implementation project(':logging')

    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2"

    testImplementation "org.junit.jupiter:junit-jupiter:5.5.2"
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2"
}
```

#### 4. 创建占位文件

**broker/src/main/kotlin/com/agmtopy/kocketmq/broker/config/BrokerConfig.kt**
```kotlin
package com.agmtopy.kocketmq.broker.config

data class BrokerConfig(
    val brokerName: String = "DefaultBroker",
    val brokerId: Long = 0,
    val clusterName: String = "DefaultCluster",
    val listenPort: Int = 10911,
    val storePathRootDir: String = "/tmp/kocketmq/store"
)
```

#### 5. 验证编译

```bash
./gradlew :broker:build
```

### 预期结果

- [ ] broker模块出现在`./gradlew projects`列表中
- [ ] `./gradlew :broker:build`成功通过
- [ ] 包结构符合设计文档

---

## 第二步：实现MappedFile

**任务：** 1.1.2 实现MappedFile
**时间：** 1天
**优先级：** P0

### 参考代码

详细实现请参考设计文档：
- `/mnt/d/project/kocketmq/docs/superpowers/specs/2026-03-20-broker-core-design.md`
- 搜索 "MappedFile" 章节

### 关键接口

```kotlin
class MappedFile(
    val fileName: String,
    val fileSize: Int
) {
    // 追加消息
    suspend fun appendMessage(data: ByteBuffer): AppendMessageResult

    // 读取消息
    fun getMessage(offset: Int, size: Int): ByteBuffer?

    // 刷盘
    suspend fun flush(flushLeastPages: Int = 0): Boolean

    // 判断文件是否满
    fun isFull(): Boolean
}
```

### 测试用例

参考实施计划中的测试代码。

---

## 验证清单

完成第一步后，请验证：

```bash
# 1. 检查模块结构
./gradlew projects

# 2. 编译broker模块
./gradlew :broker:build

# 3. 检查包结构
tree broker/src/main/kotlin/com/agmtopy/kocketmq/broker/
```

---

## 需要帮助？

如果遇到问题，可以：
1. 查看设计文档：`docs/superpowers/specs/2026-03-20-broker-core-design.md`
2. 查看实施计划：`docs/superpowers/plans/2026-03-20-broker-implementation-plan.md`
3. 参考现有remoting模块的实现模式

---

**准备好了吗？** 让我们开始实施第一个任务！
