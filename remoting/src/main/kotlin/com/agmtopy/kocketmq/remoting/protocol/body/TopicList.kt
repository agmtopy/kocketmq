package com.agmtopy.kocketmq.remoting.protocol.body

import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import java.util.HashSet

/**
 * broker addr 对应多个topic
 */
class TopicList : RemotingSerializable() {
    var brokerAddr: String? = null
    var topicList: HashSet<String> = HashSet()
}