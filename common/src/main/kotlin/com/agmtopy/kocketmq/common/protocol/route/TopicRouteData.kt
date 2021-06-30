package com.agmtopy.kocketmq.common.protocol.route

import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import com.agmtopy.kocketmq.remoting.protocol.structure.BrokerData
import com.agmtopy.kocketmq.remoting.protocol.structure.QueueData


class TopicRouteData : RemotingSerializable() {
    var orderTopicConf: String? = null
    private var queueDatas: MutableList<QueueData>? = null
    private var brokerDatas: MutableList<BrokerData>? = null
    var filterServerTable: HashMap<String?, List<String?>?>? = null

    fun cloneTopicRouteData(): TopicRouteData {
        val topicRouteData = TopicRouteData()
        topicRouteData.setQueueDatas(ArrayList<QueueData>())
        topicRouteData.setBrokerDatas(ArrayList<BrokerData>())
        topicRouteData.filterServerTable = HashMap()
        topicRouteData.orderTopicConf = orderTopicConf
        if (queueDatas != null) {
            topicRouteData.getQueueDatas()!!.addAll(queueDatas!!)
        }
        if (brokerDatas != null) {
            topicRouteData.getBrokerDatas()!!.addAll(brokerDatas!!)
        }
        if (filterServerTable != null) {
            topicRouteData.filterServerTable!!.putAll(filterServerTable!!)
        }
        return topicRouteData
    }

    fun getQueueDatas(): MutableList<QueueData>? {
        return queueDatas
    }

    fun setQueueDatas(queueDatas: MutableList<QueueData>?) {
        this.queueDatas = queueDatas
    }

    fun getBrokerDatas(): MutableList<BrokerData>? {
        return brokerDatas
    }

    fun setBrokerDatas(brokerDatas: MutableList<BrokerData>?) {
        this.brokerDatas = brokerDatas
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + if (brokerDatas == null) 0 else brokerDatas.hashCode()
        result = prime * result + if (orderTopicConf == null) 0 else orderTopicConf.hashCode()
        result = prime * result + if (queueDatas == null) 0 else queueDatas.hashCode()
        result = prime * result + if (filterServerTable == null) 0 else filterServerTable.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as TopicRouteData
        if (brokerDatas == null) {
            if (other.brokerDatas != null) return false
        } else if (brokerDatas != other.brokerDatas) return false
        if (orderTopicConf == null) {
            if (other.orderTopicConf != null) return false
        } else if (orderTopicConf != other.orderTopicConf) return false
        if (queueDatas == null) {
            if (other.queueDatas != null) return false
        } else if (queueDatas != other.queueDatas) return false
        if (filterServerTable == null) {
            if (other.filterServerTable != null) return false
        } else if (filterServerTable != other.filterServerTable) return false
        return true
    }

    override fun toString(): String {
        return ("TopicRouteData [orderTopicConf=" + orderTopicConf + ", queueDatas=" + queueDatas
                + ", brokerDatas=" + brokerDatas + ", filterServerTable=" + filterServerTable + "]")
    }
}