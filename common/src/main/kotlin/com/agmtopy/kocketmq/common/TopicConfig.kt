package com.agmtopy.kocketmq.common

import com.agmtopy.kocketmq.common.constant.PermName
import com.agmtopy.kocketmq.common.enum.TopicFilterType

class TopicConfig {
    var topicName: String? = null
    var readQueueNums = defaultReadQueueNums
    var writeQueueNums = defaultWriteQueueNums
    var perm: Int = PermName.PERM_READ or PermName.PERM_WRITE
    private var topicFilterType: TopicFilterType? = TopicFilterType.SINGLE_TAG
    var topicSysFlag = 0
    var isOrder = false

    constructor() {}
    constructor(topicName: String?) {
        this.topicName = topicName
    }

    constructor(topicName: String?, readQueueNums: Int, writeQueueNums: Int, perm: Int) {
        this.topicName = topicName
        this.readQueueNums = readQueueNums
        this.writeQueueNums = writeQueueNums
        this.perm = perm
    }

    fun encode(): String {
        val sb = StringBuilder()
        sb.append(topicName)
        sb.append(SEPARATOR)
        sb.append(readQueueNums)
        sb.append(SEPARATOR)
        sb.append(writeQueueNums)
        sb.append(SEPARATOR)
        sb.append(perm)
        sb.append(SEPARATOR)
        sb.append(topicFilterType)
        return sb.toString()
    }

    fun decode(`in`: String): Boolean {
        val strs = `in`.split(SEPARATOR).toTypedArray()
        if (strs != null && strs.size == 5) {
            topicName = strs[0]
            readQueueNums = strs[1].toInt()
            writeQueueNums = strs[2].toInt()
            perm = strs[3].toInt()
            topicFilterType = TopicFilterType.valueOf(strs[4])
            return true
        }
        return false
    }

    fun getTopicFilterType(): TopicFilterType? {
        return topicFilterType
    }

    fun setTopicFilterType(topicFilterType: TopicFilterType?) {
        this.topicFilterType = topicFilterType
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as TopicConfig
        if (readQueueNums != that.readQueueNums) return false
        if (writeQueueNums != that.writeQueueNums) return false
        if (perm != that.perm) return false
        if (topicSysFlag != that.topicSysFlag) return false
        if (isOrder != that.isOrder) return false
        return if (if (topicName != null) topicName != that.topicName else that.topicName != null) false else topicFilterType === that.topicFilterType
    }

    override fun hashCode(): Int {
        var result = if (topicName != null) topicName.hashCode() else 0
        result = 31 * result + readQueueNums
        result = 31 * result + writeQueueNums
        result = 31 * result + perm
        result = 31 * result + if (topicFilterType != null) topicFilterType.hashCode() else 0
        result = 31 * result + topicSysFlag
        result = 31 * result + if (isOrder) 1 else 0
        return result
    }

    override fun toString(): String {
        return ("TopicConfig [topicName=" + topicName + ", readQueueNums=" + readQueueNums
                + ", writeQueueNums=" + writeQueueNums + ", perm=" + PermName.perm2String(perm)
                + ", topicFilterType=" + topicFilterType + ", topicSysFlag=" + topicSysFlag + ", order="
                + isOrder + "]")
    }

    companion object {
        private const val SEPARATOR = " "
        var defaultReadQueueNums = 16
        var defaultWriteQueueNums = 16
    }
}