package com.agmtopy.kocketmq.common.protocol.body

import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import com.agmtopy.kocketmq.common.protocol.route.BrokerData

class ClusterInfo : RemotingSerializable() {
    private var brokerAddrTable: java.util.HashMap<String, BrokerData>? = null
    var clusterAddrTable: java.util.HashMap<String, MutableSet<String>>? = null

    fun getBrokerAddrTable(): java.util.HashMap<String, BrokerData>? {
        return brokerAddrTable
    }

    fun setBrokerAddrTable(brokerAddrTable: java.util.HashMap<String, BrokerData>?) {
        this.brokerAddrTable = brokerAddrTable
    }

    fun retrieveAllAddrByCluster(cluster: String): Array<String> {
        val addrs: MutableList<String> = ArrayList()
        if (clusterAddrTable!!.containsKey(cluster)) {
            val brokerNames = clusterAddrTable!![cluster]!!
            for (brokerName in brokerNames) {
                val brokerData: BrokerData? = brokerAddrTable!![brokerName]
                if (null != brokerData) {
                    addrs.addAll(brokerData.brokerAddrs!!.values)
                }
            }
        }
        return addrs.toTypedArray()
    }

    fun retrieveAllClusterNames(): Array<String> {
        return clusterAddrTable!!.keys.toTypedArray()
    }
}