package com.agmtopy.kocketmq.namesrv

import com.agmtopy.kocketmq.common.MixAll
import com.agmtopy.kocketmq.common.TopicConfig
import com.agmtopy.kocketmq.common.protocol.body.TopicConfigSerializeWrapper
import com.agmtopy.kocketmq.logging.routeinfo.BrokerHousekeepingService
import com.agmtopy.kocketmq.logging.routeinfo.RouteInfoManager
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BrokerHousekeepingServiceTest {

    @Test
    fun `should remove route data when channel is closed`() {
        TestLoggerBootstrap.ensureRegistered()
        val routeInfoManager = RouteInfoManager()
        val housekeepingService = BrokerHousekeepingService(routeInfoManager)

        val topicConfigWrapper = TopicConfigSerializeWrapper().apply {
            topicConfigTable["topic-a"] = TopicConfig("topic-a")
        }

        val channel = EmbeddedChannel()
        routeInfoManager.registerBroker(
            "cluster-a",
            "127.0.0.1:10911",
            "broker-a",
            MixAll.MASTER_ID,
            "127.0.0.1:10912",
            topicConfigWrapper,
            null,
            channel
        )

        assertNotNull(routeInfoManager.pickupTopicRouteData("topic-a"))

        housekeepingService.onChannelClose("127.0.0.1:10911", channel)

        assertNull(routeInfoManager.pickupTopicRouteData("topic-a"))
    }
}
