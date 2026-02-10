package com.agmtopy.kocketmq.namesrv

import com.agmtopy.kocketmq.common.namesrv.NamesrvConfig
import com.agmtopy.kocketmq.common.protocol.body.KVTable
import com.agmtopy.kocketmq.logging.NamesrvController
import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import com.agmtopy.kocketmq.remoting.netty.NettyServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files

class KVConfigManagerTest {

    @Test
    fun `should put get delete and list kv configs`() {
        TestLoggerBootstrap.ensureRegistered()
        val tempDir = Files.createTempDirectory("kocketmq-kv")
        val kvConfigPath = tempDir.resolve("kvConfig.json").toString()

        val namesrvConfig = NamesrvConfig().apply {
            this.kvConfigPath = kvConfigPath
        }
        val controller = NamesrvController(namesrvConfig, NettyServerConfig())

        controller.kvConfigManager.putKVConfig("ns-test", "k1", "v1")
        controller.kvConfigManager.putKVConfig("ns-test", "k2", "v2")

        assertEquals("v1", controller.kvConfigManager.getKVConfig("ns-test", "k1"))
        assertEquals("v2", controller.kvConfigManager.getKVConfig("ns-test", "k2"))

        val body = controller.kvConfigManager.getKVListByNamespace("ns-test")
        val table = RemotingSerializable.decode(body, KVTable::class.java)
        assertEquals("v1", table.table["k1"])
        assertEquals("v2", table.table["k2"])

        controller.kvConfigManager.deleteKVConfig("ns-test", "k1")
        assertNull(controller.kvConfigManager.getKVConfig("ns-test", "k1"))
    }
}
