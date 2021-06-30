package com.agmtopy.kocketmq.common.protocol.body

import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import java.util.HashMap

class KVTable : RemotingSerializable() {
    var table = HashMap<String?, String?>()
}