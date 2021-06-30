package com.agmtopy.kocketmq.remoting.protocol.structure

import com.agmtopy.kocketmq.common.DataVersion
import io.netty.channel.Channel

/**
 * 数据类摒弃作为内部类的写法
 */
class BrokerLiveInfo(
    var lastUpdateTimestamp: Long, dataVersion: DataVersion, channel: Channel,
    haServerAddr: String
) {
    private var dataVersion: DataVersion
    var channel: Channel
    var haServerAddr: String

    fun getDataVersion(): DataVersion {
        return dataVersion
    }

    fun setDataVersion(dataVersion: DataVersion) {
        this.dataVersion = dataVersion
    }

    override fun toString(): String {
        return ("BrokerLiveInfo [lastUpdateTimestamp=" + lastUpdateTimestamp + ", dataVersion=" + dataVersion
                + ", channel=" + channel + ", haServerAddr=" + haServerAddr + "]")
    }

    init {
        this.dataVersion = dataVersion
        this.channel = channel
        this.haServerAddr = haServerAddr
    }
}
