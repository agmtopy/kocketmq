package com.agmtopy.kocketmq.remoting.protocol.structure

import com.agmtopy.kocketmq.remoting.protocol.structure.QueueData

class QueueData : Comparable<QueueData> {
    var brokerName: String? = null
    var readQueueNums = 0
    var writeQueueNums = 0
    var perm = 0
    var topicSynFlag = 0
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + if (brokerName == null) 0 else brokerName.hashCode()
        result = prime * result + perm
        result = prime * result + readQueueNums
        result = prime * result + writeQueueNums
        result = prime * result + topicSynFlag
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as QueueData
        if (brokerName == null) {
            if (other.brokerName != null) return false
        } else if (brokerName != other.brokerName) return false
        if (perm != other.perm) return false
        if (readQueueNums != other.readQueueNums) return false
        if (writeQueueNums != other.writeQueueNums) return false
        return if (topicSynFlag != other.topicSynFlag) false else true
    }

    override fun toString(): String {
        return ("QueueData [brokerName=" + brokerName + ", readQueueNums=" + readQueueNums
                + ", writeQueueNums=" + writeQueueNums + ", perm=" + perm + ", topicSynFlag=" + topicSynFlag
                + "]")
    }

    override fun compareTo(o: QueueData): Int {
        return brokerName!!.compareTo(o.brokerName!!)
    }
}