package com.agmtopy.kocketmq.common.protocol.body

import com.agmtopy.kocketmq.common.DataVersion
import com.agmtopy.kocketmq.common.TopicConfig
import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class TopicConfigSerializeWrapper : RemotingSerializable() {
    var topicConfigTable: ConcurrentMap<String, TopicConfig> = ConcurrentHashMap<String, TopicConfig>()
    var dataVersion: DataVersion = DataVersion()
}
