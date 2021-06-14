package com.agmtopy.kocketmq.logging.kvconfig

import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable

import java.util.*

class KVConfigSerializeWrapper: RemotingSerializable() {
    private var configTable: Map<String?, Map<String?, String?>?>? = null

    fun getConfigTable(): Map<String?, Map<String?, String?>?>? {
        return configTable
    }

    fun setConfigTable(configTable: Map<String?, Map<String?, String?>?>?) {
        this.configTable = configTable
    }
}