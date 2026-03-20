package com.agmtopy.kocketmq.broker.stats

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * BrokerStats测试
 */
class BrokerStatsTest {

    private lateinit var brokerStats: BrokerStats

    @BeforeEach
    fun setUp() {
        brokerStats = BrokerStats()
    }

    @Test
    fun `测试记录消息发送`() {
        brokerStats.recordMsgPut(success = true, size = 100)
        brokerStats.recordMsgPut(success = true, size = 200)
        brokerStats.recordMsgPut(success = false, size = 50)

        assertEquals(3, brokerStats.getMsgPutTotal())
        assertEquals(2, brokerStats.getMsgPutSuccess())
        assertEquals(1, brokerStats.getMsgPutFailed())
        assertEquals(300, brokerStats.getMsgPutSizeTotal())
    }

    @Test
    fun `测试记录消息拉取`() {
        brokerStats.recordMsgGet(success = true, size = 100)
        brokerStats.recordMsgGet(success = false, size = 50)

        assertEquals(2, brokerStats.getMsgGetTotal())
        assertEquals(1, brokerStats.getMsgGetSuccess())
        assertEquals(1, brokerStats.getMsgGetFailed())
        assertEquals(100, brokerStats.getMsgGetSizeTotal())
    }

    @Test
    fun `测试TPS更新`() {
        // 记录一些操作
        for (i in 1..10) {
            brokerStats.recordMsgPut(success = true, size = 100)
        }

        // TPS应该为0（还未更新）
        assertEquals(0, brokerStats.getPutTps())

        // 等待1秒后更新TPS
        Thread.sleep(1000)
        brokerStats.updateTps()

        // TPS应该大于0
        assertTrue(brokerStats.getPutTps() > 0)
    }

    @Test
    fun `测试获取统计信息`() {
        brokerStats.recordMsgPut(success = true, size = 100)
        brokerStats.recordMsgGet(success = true, size = 50)

        val statsInfo = brokerStats.getStatsInfo()

        assertTrue(statsInfo.containsKey("msgPutTotal"))
        assertTrue(statsInfo.containsKey("msgGetTotal"))
        assertEquals(1L, statsInfo["msgPutTotal"])
        assertEquals(1L, statsInfo["msgGetTotal"])
    }

    @Test
    fun `测试重置统计`() {
        brokerStats.recordMsgPut(success = true, size = 100)
        brokerStats.recordMsgGet(success = true, size = 50)

        brokerStats.reset()

        assertEquals(0, brokerStats.getMsgPutTotal())
        assertEquals(0, brokerStats.getMsgGetTotal())
        assertEquals(0, brokerStats.getMsgPutSizeTotal())
        assertEquals(0, brokerStats.getMsgGetSizeTotal())
    }
}
