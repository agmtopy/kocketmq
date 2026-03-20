package com.agmtopy.kocketmq.broker.offset

import com.agmtopy.kocketmq.broker.config.BrokerConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ConsumerOffsetManager测试
 */
class ConsumerOffsetManagerTest {

    private val testDir = "/tmp/kocketmq/test/consumeroffset"
    private lateinit var brokerConfig: BrokerConfig
    private lateinit var offsetManager: ConsumerOffsetManager

    @BeforeEach
    fun setUp() {
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()

        brokerConfig = BrokerConfig(
            storePathRootDir = testDir,
            consumerOffsetPath = "$testDir/config/consumerOffset.json"
        )

        offsetManager = ConsumerOffsetManager(brokerConfig)
    }

    @Test
    fun `test load with empty config`() = runBlocking {
        val loaded = offsetManager.load()
        assertTrue(loaded)
        assertEquals(0, offsetManager.getOffsetTableSize())
    }

    @Test
    fun `test commit and query offset`() = runBlocking {
        offsetManager.load()

        // 提交offset
        offsetManager.commitOffset("TestTopic", "TestGroup", 0, 100L)
        offsetManager.commitOffset("TestTopic", "TestGroup", 1, 200L)

        // 查询offset
        assertEquals(100L, offsetManager.queryOffset("TestTopic", "TestGroup", 0))
        assertEquals(200L, offsetManager.queryOffset("TestTopic", "TestGroup", 1))
        assertEquals(-1L, offsetManager.queryOffset("TestTopic", "TestGroup", 2))  // 不存在
    }

    @Test
    fun `test query all offset`() = runBlocking {
        offsetManager.load()

        offsetManager.commitOffset("TestTopic", "TestGroup", 0, 100L)
        offsetManager.commitOffset("TestTopic", "TestGroup", 1, 200L)
        offsetManager.commitOffset("TestTopic", "TestGroup", 2, 300L)

        val allOffset = offsetManager.queryAllOffset("TestTopic", "TestGroup")

        assertEquals(3, allOffset.size)
        assertEquals(100L, allOffset[0])
        assertEquals(200L, allOffset[1])
        assertEquals(300L, allOffset[2])
    }

    @Test
    fun `test multiple groups`() = runBlocking {
        offsetManager.load()

        // 不同消费者组
        offsetManager.commitOffset("TestTopic", "Group1", 0, 100L)
        offsetManager.commitOffset("TestTopic", "Group2", 0, 200L)

        assertEquals(100L, offsetManager.queryOffset("TestTopic", "Group1", 0))
        assertEquals(200L, offsetManager.queryOffset("TestTopic", "Group2", 0))
    }

    @Test
    fun `test remove offset by queue`() = runBlocking {
        offsetManager.load()

        offsetManager.commitOffset("TestTopic", "TestGroup", 0, 100L)
        offsetManager.commitOffset("TestTopic", "TestGroup", 1, 200L)

        // 删除单个队列
        offsetManager.removeOffset("TestTopic", "TestGroup", 0)

        assertEquals(-1L, offsetManager.queryOffset("TestTopic", "TestGroup", 0))  // 已删除
        assertEquals(200L, offsetManager.queryOffset("TestTopic", "TestGroup", 1))  // 保留
    }

    @Test
    fun `test remove all offset for topic`() = runBlocking {
        offsetManager.load()

        offsetManager.commitOffset("TestTopic", "TestGroup", 0, 100L)
        offsetManager.commitOffset("TestTopic", "TestGroup", 1, 200L)

        // 删除整个Topic
        offsetManager.removeOffset("TestTopic", "TestGroup")

        assertEquals(-1L, offsetManager.queryOffset("TestTopic", "TestGroup", 0))
        assertEquals(-1L, offsetManager.queryOffset("TestTopic", "TestGroup", 1))

        val allOffset = offsetManager.queryAllOffset("TestTopic", "TestGroup")
        assertTrue(allOffset.isEmpty())
    }

    @Test
    fun `test persist`() = runBlocking {
        offsetManager.load()

        offsetManager.commitOffset("TestTopic", "TestGroup", 0, 100L)
        offsetManager.commitOffset("TestTopic", "TestGroup", 1, 200L)

        // 持久化
        offsetManager.persist()

        // TODO: 验证文件创建
        // val configFile = File(brokerConfig.consumerOffsetPath)
        // assertTrue(configFile.exists())
    }

    @Test
    fun `test offset table size`() = runBlocking {
        offsetManager.load()
        assertEquals(0, offsetManager.getOffsetTableSize())

        offsetManager.commitOffset("Topic1", "Group1", 0, 100L)
        assertEquals(1, offsetManager.getOffsetTableSize())

        offsetManager.commitOffset("Topic2", "Group1", 0, 200L)
        assertEquals(2, offsetManager.getOffsetTableSize())

        // 同一个topic-group，不同queue不会增加计数
        offsetManager.commitOffset("Topic1", "Group1", 1, 150L)
        assertEquals(2, offsetManager.getOffsetTableSize())
    }

    @Test
    fun `test get all offset table`() = runBlocking {
        offsetManager.load()

        offsetManager.commitOffset("Topic1", "Group1", 0, 100L)
        offsetManager.commitOffset("Topic1", "Group1", 1, 150L)
        offsetManager.commitOffset("Topic2", "Group2", 0, 200L)

        val allTable = offsetManager.getAllOffsetTable()

        assertEquals(2, allTable.size)
        assertTrue(allTable.containsKey("Topic1@Group1"))
        assertTrue(allTable.containsKey("Topic2@Group2"))
    }
}
