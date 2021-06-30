package com.agmtopy.kocketmq.common

import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import java.util.concurrent.atomic.AtomicLong

/**
 * version OBJ
 */
class DataVersion : RemotingSerializable() {
    private var timestamp = System.currentTimeMillis()
    private var counter: AtomicLong? = AtomicLong(0)
    fun assignNewOne(dataVersion: DataVersion) {
        timestamp = dataVersion.timestamp
        counter!!.set(dataVersion.counter!!.get())
    }

    fun nextVersion() {
        timestamp = System.currentTimeMillis()
        counter!!.incrementAndGet()
    }

    fun getTimestamp(): Long {
        return timestamp
    }

    fun setTimestamp(timestamp: Long) {
        this.timestamp = timestamp
    }

    fun getCounter(): AtomicLong? {
        return counter
    }

    fun setCounter(counter: AtomicLong?) {
        this.counter = counter
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as DataVersion
        if (timestamp != that.timestamp) {
            return false
        }
        return if (counter != null && that.counter != null) {
            counter!!.toLong() == that.counter!!.toLong()
        } else null == counter && null == that.counter
    }

    override fun hashCode(): Int {
        var result = (timestamp xor (timestamp ushr 32)).toInt()
        if (null != counter) {
            val l = counter!!.get()
            result = 31 * result + (l xor (l ushr 32)).toInt()
        }
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder("DataVersion[")
        sb.append("timestamp=").append(timestamp)
        sb.append(", counter=").append(counter)
        sb.append(']')
        return sb.toString()
    }
}