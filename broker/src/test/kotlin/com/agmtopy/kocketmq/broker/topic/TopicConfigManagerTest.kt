package com.agmtopy.kocketmq.broker.topic

import com.agmtopy.kocketmq.broker.config.BrokerConfig
import com.agmtopy.kocketmq.common.TopicConfig
import com.agmtopy.kocketmq.common.constant.PermName
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * TopicConfigManager测试
 */
class TopicConfigManagerTest {

    private val testDir = "/tmp/kocketmq/test/topicconfig"
    private lateinit var brokerConfig: BrokerConfig
    private lateinit var topicConfigManager: TopicConfigManager

    @BeforeEach
    fun setUp() {
        File(testDir).deleteRecursively()
        File(testDir).mkdirs()

        brokerConfig = BrokerConfig(
            storePathRootDir = testDir,
            topicConfigPath = "$testDir/config/topics.json",
            autoCreateTopicEnable = true,
            defaultTopicQueueNums = 8
        )

        topicConfigManager = TopicConfigManager(brokerConfig)
    }

    @Test
    fun `test load with empty config`() = runBlocking {
        val loaded = topicConfigManager.load()
        assertTrue(loaded)

        // 应该创建默认Topic
        val defaultTopic = topicConfigManager.getTopicConfig(TopicConfigManager.DEFAULT_TOPIC)
        assertNotNull(defaultTopic)
        assertEquals(8, defaultTopic!!.readQueueNums)
    }

    @Test
    fun `test update and get topic config`() = runBlocking {
        topicConfigManager.load()

        val topicConfig = TopicConfig(
            topicName = "TestTopic",
            readQueueNums = 4,
            writeQueueNums = 4,
            perm = PermName.PERM_READ or PermName.PERM_WRITE
        )

        topicConfigManager.updateTopicConfig(topicConfig)

        val retrieved = topicConfigManager.getTopicConfig("TestTopic")
        assertNotNull(retrieved)
        assertEquals("TestTopic", retrieved!!.topicName)
        assertEquals(4, retrieved.readQueueNums)
        assertEquals(PermName.PERM_READ or PermName.PERM_WRITE, retrieved.perm)
    }

    @Test
    fun `test auto create topic`() = runBlocking {
        topicConfigManager.load()

        // 自动创建Topic
        val topicConfig = topicConfigManager.getTopicConfigOrCreate("AutoCreatedTopic")
        assertNotNull(topicConfig)
        assertEquals("AutoCreatedTopic", topicConfig!!.topicName)
        assertEquals(8, topicConfig.readQueueNums)  // 使用默认队列数
    }

    @Test
    fun `test auto create disabled`() = runBlocking {
        brokerConfig = BrokerConfig(
            storePathRootDir = testDir,
            topicConfigPath = "$testDir/config/topics.json",
            autoCreateTopicEnable = false,
            defaultTopicQueueNums = 8
        )
        topicConfigManager = TopicConfigManager(brokerConfig)
        topicConfigManager.load()

        // 不应该自动创建
        val topicConfig = topicConfigManager.getTopicConfigOrCreate("NewTopic")
        assertNull(topicConfig)
    }

    @Test
    fun `test delete topic config`() = runBlocking {
        topicConfigManager.load()

        val topicConfig = TopicConfig(
            topicName = "ToDelete",
            readQueueNums = 4,
            writeQueueNums = 4,
            perm = PermName.PERM_READ or PermName.PERM_WRITE
        )

        topicConfigManager.updateTopicConfig(topicConfig)
        assertNotNull(topicConfigManager.getTopicConfig("ToDelete"))

        topicConfigManager.deleteTopicConfig("ToDelete")
        assertNull(topicConfigManager.getTopicConfig("ToDelete"))
    }

    @Test
    fun `test get all topic config`() = runBlocking {
        topicConfigManager.load()

        topicConfigManager.updateTopicConfig(TopicConfig("Topic1", 4, 4, PermName.PERM_READ or PermName.PERM_WRITE))
        topicConfigManager.updateTopicConfig(TopicConfig("Topic2", 8, 8, PermName.PERM_READ or PermName.PERM_WRITE))

        val allConfig = topicConfigManager.getAllTopicConfig()
        assertTrue(allConfig.size >= 2)  // 包含默认Topic
        assertTrue(allConfig.containsKey("Topic1"))
        assertTrue(allConfig.containsKey("Topic2"))
    }

    @Test
    fun `test persist`() = runBlocking {
        topicConfigManager.load()

        topicConfigManager.updateTopicConfig(TopicConfig("PersistTest", 4, 4, PermName.PERM_READ or PermName.PERM_WRITE))

        // 持久化
        topicConfigManager.persist()

        // TODO: 验证文件创建
        // val configFile = File(brokerConfig.topicConfigPath)
        // assertTrue(configFile.exists())
    }

    @Test
    fun `test get topic count`() = runBlocking {
        topicConfigManager.load()
        assertEquals(1, topicConfigManager.getTopicCount())  // 默认Topic

        topicConfigManager.updateTopicConfig(TopicConfig("Topic1", 4, 4, PermName.PERM_READ or PermName.PERM_WRITE))
        assertEquals(2, topicConfigManager.getTopicCount())

        topicConfigManager.updateTopicConfig(TopicConfig("Topic2", 8, 8, PermName.PERM_READ or PermName.PERM_WRITE))
        assertEquals(3, topicConfigManager.getTopicCount())
    }
}
