package com.agmtopy.kocketmq.remoting.protocol.structure

import com.agmtopy.kocketmq.common.MixAll
import java.util.*

/**
 * Broker Data
 */
class BrokerData : Comparable<BrokerData> {
    var cluster: String? = null
    var brokerName: String? = null
    var brokerAddrs: HashMap<Long, String>? = null
    private val random = Random()

    constructor() {}
    constructor(cluster: String?, brokerName: String?, brokerAddrs: HashMap<Long, String>?) {
        this.cluster = cluster
        this.brokerName = brokerName
        this.brokerAddrs = brokerAddrs
    }

    /**
     * Selects a (preferably master) broker address from the registered list.
     * If the master's address cannot be found, a slave broker address is selected in a random manner.
     *
     * @return Broker address.
     */
    fun selectBrokerAddr(): String {
        val addr = brokerAddrs!![MixAll.MASTER_ID]
        if (addr == null) {
            val addrs: List<String> = ArrayList(brokerAddrs!!.values)
            return addrs[random.nextInt(addrs.size)]
        }
        return addr
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + if (brokerAddrs == null) 0 else brokerAddrs.hashCode()
        result = prime * result + if (brokerName == null) 0 else brokerName.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as BrokerData
        if (brokerAddrs == null) {
            if (other.brokerAddrs != null) return false
        } else if (brokerAddrs != other.brokerAddrs) return false
        if (brokerName == null) {
            if (other.brokerName != null) return false
        } else if (brokerName != other.brokerName) return false
        return true
    }

    override fun toString(): String {
        return "BrokerData [brokerName=$brokerName, brokerAddrs=$brokerAddrs]"
    }

    override fun compareTo(o: BrokerData): Int {
        return brokerName!!.compareTo(o.brokerName!!)
    }
}