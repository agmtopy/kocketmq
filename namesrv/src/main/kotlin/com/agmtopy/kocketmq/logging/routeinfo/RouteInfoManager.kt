package com.agmtopy.kocketmq.logging.routeinfo

import com.agmtopy.kocketmq.common.DataVersion
import com.agmtopy.kocketmq.common.MixAll
import com.agmtopy.kocketmq.common.TopicConfig
import com.agmtopy.kocketmq.common.TopicSysFlag
import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.common.constant.PermName
import com.agmtopy.kocketmq.common.namesrv.RegisterBrokerResult
import com.agmtopy.kocketmq.common.protocol.body.ClusterInfo
import com.agmtopy.kocketmq.common.protocol.body.TopicConfigSerializeWrapper
import com.agmtopy.kocketmq.common.protocol.route.TopicRouteData
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.common.RemotingUtil
import com.agmtopy.kocketmq.remoting.protocol.body.TopicList
import com.agmtopy.kocketmq.remoting.protocol.structure.BrokerData
import com.agmtopy.kocketmq.remoting.protocol.structure.BrokerLiveInfo
import com.agmtopy.kocketmq.remoting.protocol.structure.QueueData
import io.netty.channel.Channel
import java.util.*
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * routeInfo manager
 */
class RouteInfoManager {
    private val log: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME)
    private val BROKER_CHANNEL_EXPIRED_TIME = (1000 * 60 * 2).toLong()
    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private var topicQueueTable: HashMap<String, MutableList<QueueData>?>? = null
    private var brokerAddrTable: HashMap<String, BrokerData>? = null
    private var clusterAddrTable: HashMap<String, MutableSet<String>>? = null
    private var brokerLiveTable: HashMap<String, BrokerLiveInfo>? = null
    private var filterServerTable: HashMap<String?, List<String>>? = null

    constructor() {
        topicQueueTable = HashMap<String, MutableList<QueueData>?>(1024)
        brokerAddrTable = HashMap<String, BrokerData>(128)
        clusterAddrTable = HashMap(32)
        brokerLiveTable = HashMap<String, BrokerLiveInfo>(256)
        filterServerTable = HashMap(256)
    }

    fun getAllClusterInfo(): ByteArray? {
        val clusterInfoSerializeWrapper = ClusterInfo()
        clusterInfoSerializeWrapper.setBrokerAddrTable(brokerAddrTable)
        clusterInfoSerializeWrapper.clusterAddrTable = clusterAddrTable
        return clusterInfoSerializeWrapper.encode()
    }

    fun deleteTopic(topic: String) {
        try {
            try {
                lock.writeLock().lockInterruptibly()
                topicQueueTable!!.remove(topic)
            } finally {
                lock.writeLock().unlock()
            }
        } catch (e: Exception) {
            log.error("deleteTopic Exception", e)
        }
    }

    fun getAllTopicList(): ByteArray? {
        val topicList = TopicList()
        try {
            try {
                lock.readLock().lockInterruptibly()
                topicList.topicList.addAll(topicQueueTable!!.keys)
            } finally {
                lock.readLock().unlock()
            }
        } catch (e: Exception) {
            log.error("getAllTopicList Exception", e)
        }
        return topicList.encode()
    }

    fun registerBroker(
        clusterName: String,
        brokerAddr: String?,
        brokerName: String,
        brokerId: Long,
        haServerAddr: String?,
        topicConfigWrapper: TopicConfigSerializeWrapper?,
        filterServerList: List<String>?,
        channel: Channel?
    ): RegisterBrokerResult? {
        val result = RegisterBrokerResult()
        try {
            try {
                lock.writeLock().lockInterruptibly()
                var brokerNames = clusterAddrTable!![clusterName]
                if (null == brokerNames) {
                    brokerNames = HashSet()
                    clusterAddrTable!![clusterName] = brokerNames
                }
                brokerNames.add(brokerName)
                var registerFirst = false
                var brokerData: BrokerData? = brokerAddrTable!![brokerName]
                if (null == brokerData) {
                    registerFirst = true
                    brokerData = BrokerData(clusterName, brokerName, HashMap<Long, String>())
                    brokerAddrTable!![brokerName] = brokerData
                }
                val brokerAddrsMap: HashMap<Long, String> = brokerData.brokerAddrs!!
                //Switch slave to master: first remove <1, IP:PORT> in namesrv, then add <0, IP:PORT>
                //The same IP:PORT must only have one record in brokerAddrTable
                val it: MutableIterator<Map.Entry<Long, String>> = brokerAddrsMap.entries.iterator()
                while (it.hasNext()) {
                    val (key, value) = it.next()
                    if (null != brokerAddr && brokerAddr == value && brokerId != key) {
                        it.remove()
                    }
                }
                val oldAddr: String? = brokerData.brokerAddrs!!.put(brokerId, brokerAddr!!)
                registerFirst = registerFirst || null == oldAddr
                if (null != topicConfigWrapper
                    && MixAll.MASTER_ID === brokerId
                ) {
                    if (isBrokerTopicConfigChanged(brokerAddr, topicConfigWrapper.dataVersion)
                        || registerFirst
                    ) {
                        val tcTable: ConcurrentMap<String, TopicConfig> = topicConfigWrapper.topicConfigTable
                        if (tcTable != null) {
                            for ((_, value) in tcTable) {
                                createAndUpdateQueueData(brokerName, value)
                            }
                        }
                    }
                }
                val prevBrokerLiveInfo: BrokerLiveInfo? = brokerLiveTable!!.put(
                    brokerAddr,
                    BrokerLiveInfo(
                        System.currentTimeMillis(),
                        topicConfigWrapper!!.dataVersion,
                        channel!!,
                        haServerAddr!!
                    )
                )
                if (null == prevBrokerLiveInfo) {
                    log.info("new broker registered, {} HAServer: {}", brokerAddr, haServerAddr)
                }
                if (filterServerList != null) {
                    if (filterServerList.isEmpty()) {
                        filterServerTable!!.remove(brokerAddr)
                    } else {
                        filterServerTable!![brokerAddr] = filterServerList
                    }
                }
                if (MixAll.MASTER_ID !== brokerId) {
                    val masterAddr: String? = brokerData.brokerAddrs!!.get(MixAll.MASTER_ID)
                    if (masterAddr != null) {
                        val brokerLiveInfo: BrokerLiveInfo? =
                            brokerLiveTable!![masterAddr]
                        if (brokerLiveInfo != null) {
                            result.haServerAddr = brokerLiveInfo.haServerAddr
                            result.masterAddr = masterAddr
                        }
                    }
                }
            } finally {
                lock.writeLock().unlock()
            }
        } catch (e: Exception) {
            log.error("registerBroker Exception", e)
        }
        return result
    }

    fun isBrokerTopicConfigChanged(brokerAddr: String?, dataVersion: DataVersion?): Boolean {
        val prev: DataVersion? = queryBrokerTopicConfig(brokerAddr)
        return null == prev || !prev.equals(dataVersion)
    }

    fun queryBrokerTopicConfig(brokerAddr: String?): DataVersion? {
        val prev: BrokerLiveInfo? = brokerLiveTable!![brokerAddr]
        return if (prev != null) {
            prev.getDataVersion()
        } else null
    }

    fun updateBrokerInfoUpdateTimestamp(brokerAddr: String?) {
        val prev: BrokerLiveInfo? = brokerLiveTable!![brokerAddr]
        if (prev != null) {
            prev.lastUpdateTimestamp = System.currentTimeMillis()
        }
    }

    private fun createAndUpdateQueueData(brokerName: String, topicConfig: TopicConfig) {
        val queueData = QueueData()
        queueData.brokerName = brokerName
        queueData.writeQueueNums = topicConfig.writeQueueNums
        queueData.readQueueNums = topicConfig.readQueueNums
        queueData.perm = topicConfig.perm
        queueData.topicSynFlag = topicConfig.topicSysFlag
        var queueDataList: MutableList<QueueData>? = topicQueueTable!![topicConfig.topicName]
        if (null == queueDataList) {
            queueDataList = LinkedList<QueueData>()
            queueDataList!!.add(queueData)
            topicQueueTable!!.put(topicConfig.topicName!!, queueDataList)
            log.info("new topic registered, {} {}", topicConfig.topicName, queueData)
        } else {
            var addNewOne = true
            val it: MutableIterator<QueueData> = queueDataList.iterator()
            while (it.hasNext()) {
                val qd: QueueData = it.next()
                if (qd.brokerName.equals(brokerName)) {
                    if (qd.equals(queueData)) {
                        addNewOne = false
                    } else {
                        log.info("topic changed, $topicConfig.topicName OLD: $qd NEW: $queueData")
                        it.remove()
                    }
                }
            }
            if (addNewOne) {
                queueDataList.add(queueData)
            }
        }
    }

    fun wipeWritePermOfBrokerByLock(brokerName: String): Int {
        try {
            return try {
                lock.writeLock().lockInterruptibly()
                wipeWritePermOfBroker(brokerName)
            } finally {
                lock.writeLock().unlock()
            }
        } catch (e: Exception) {
            log.error("wipeWritePermOfBrokerByLock Exception", e)
        }
        return 0
    }

    private fun wipeWritePermOfBroker(brokerName: String): Int {
        var wipeTopicCnt = 0
        val itTopic: Iterator<Map.Entry<String, List<QueueData>?>> = topicQueueTable!!.entries.iterator()
        while (itTopic.hasNext()) {
            val (_, qdList) = itTopic.next()
            val it: Iterator<QueueData> = qdList!!.iterator()
            while (it.hasNext()) {
                val qd: QueueData = it.next()
                if (qd.brokerName.equals(brokerName)) {
                    var perm: Int = qd.perm
                    perm = perm and PermName.PERM_WRITE.inv()
                    qd.perm = perm
                    wipeTopicCnt++
                }
            }
        }
        return wipeTopicCnt
    }

    fun unregisterBroker(
        clusterName: String,
        brokerAddr: String?,
        brokerName: String,
        brokerId: Long
    ) {
        try {
            try {
                lock.writeLock().lockInterruptibly()
                val brokerLiveInfo: BrokerLiveInfo? =
                    brokerLiveTable!!.remove(brokerAddr)
                log.info(
                    "unregisterBroker, remove from brokerLiveTable {}, {}",
                    if (brokerLiveInfo != null) "OK" else "Failed",
                    brokerAddr
                )
                filterServerTable!!.remove(brokerAddr)
                var removeBrokerName = false
                val brokerData: BrokerData? = brokerAddrTable!![brokerName]
                if (null != brokerData) {
                    val addr: String? = brokerData.brokerAddrs!!.remove(brokerId)
                    log.info(
                        "unregisterBroker, remove addr from brokerAddrTable {}, {}",
                        if (addr != null) "OK" else "Failed",
                        brokerAddr
                    )
                    if (brokerData.brokerAddrs!!.isEmpty()) {
                        brokerAddrTable!!.remove(brokerName)
                        log.info(
                            "unregisterBroker, remove name from brokerAddrTable OK, {}",
                            brokerName
                        )
                        removeBrokerName = true
                    }
                }
                if (removeBrokerName) {
                    val nameSet = clusterAddrTable!![clusterName]
                    if (nameSet != null) {
                        val removed = nameSet.remove(brokerName)
                        log.info(
                            "unregisterBroker, remove name from clusterAddrTable {}, {}",
                            if (removed) "OK" else "Failed",
                            brokerName
                        )
                        if (nameSet.isEmpty()) {
                            clusterAddrTable!!.remove(clusterName)
                            log.info(
                                "unregisterBroker, remove cluster from clusterAddrTable {}",
                                clusterName
                            )
                        }
                    }
                    removeTopicByBrokerName(brokerName)
                }
            } finally {
                lock.writeLock().unlock()
            }
        } catch (e: Exception) {
            log.error("unregisterBroker Exception", e)
        }
    }

    private fun removeTopicByBrokerName(brokerName: String) {
        val itMap: MutableIterator<Map.Entry<String, MutableList<QueueData>?>> = topicQueueTable!!.entries.iterator()
        while (itMap.hasNext()) {
            val (topic, queueDataList) = itMap.next()
            val it: MutableIterator<QueueData> = queueDataList!!.iterator()
            while (it.hasNext()) {
                val qd: QueueData = it.next()
                if (qd.brokerName.equals(brokerName)) {
                    log.info("removeTopicByBrokerName, remove one broker's topic {} {}", topic, qd)
                    it.remove()
                }
            }
            if (queueDataList.isEmpty()) {
                log.info("removeTopicByBrokerName, remove the topic all queue {}", topic)
                itMap.remove()
            }
        }
    }

    fun pickupTopicRouteData(topic: String): TopicRouteData? {
        val topicRouteData = TopicRouteData()
        var foundQueueData = false
        var foundBrokerData = false
        val brokerNameSet: MutableSet<String> = HashSet()
        val brokerDataList: MutableList<BrokerData> = LinkedList<BrokerData>()
        topicRouteData.setBrokerDatas(brokerDataList)
        val filterServerMap = HashMap<String?, List<String?>?>()
        topicRouteData.filterServerTable = filterServerMap
        try {
            try {
                lock.readLock().lockInterruptibly()
                val queueDataList: MutableList<QueueData>? = topicQueueTable!![topic]
                if (queueDataList != null) {
                    topicRouteData.setQueueDatas(queueDataList)
                    foundQueueData = true
                    val it: Iterator<QueueData> = queueDataList.iterator()
                    while (it.hasNext()) {
                        val qd: QueueData = it.next()
                        brokerNameSet.add(qd!!.brokerName!!)
                    }
                    for (brokerName in brokerNameSet) {
                        val brokerData: BrokerData? = brokerAddrTable!![brokerName]
                        if (null != brokerData) {
                            val brokerDataClone = BrokerData(
                                brokerData.cluster,
                                brokerData.brokerName,
                                brokerData.brokerAddrs!!.clone() as HashMap<Long, String>
                            )
                            brokerDataList.add(brokerDataClone)
                            foundBrokerData = true
                            for (brokerAddr in brokerDataClone.brokerAddrs!!.values) {
                                val filterServerList = filterServerTable!![brokerAddr]!!
                                filterServerMap[brokerAddr] = filterServerList
                            }
                        }
                    }
                }
            } finally {
                lock.readLock().unlock()
            }
        } catch (e: Exception) {
            log.error("pickupTopicRouteData Exception", e)
        }
        log.debug("pickupTopicRouteData {} {}", topic, topicRouteData)
        return if (foundBrokerData && foundQueueData) {
            topicRouteData
        } else null
    }

    fun scanNotActiveBroker() {
        val it: MutableIterator<Map.Entry<String?, BrokerLiveInfo>> =
            brokerLiveTable!!.entries.iterator()
        while (it.hasNext()) {
            val (key, value) = it.next()
            val last: Long = value.lastUpdateTimestamp
            if (last + BROKER_CHANNEL_EXPIRED_TIME < System.currentTimeMillis()) {
                RemotingUtil.closeChannel(value.channel)
                it.remove()
                log.warn("The broker channel expired, {} {}ms", key, BROKER_CHANNEL_EXPIRED_TIME)
                onChannelDestroy(key, value.channel)
            }
        }
    }

    fun onChannelDestroy(remoteAddr: String?, channel: Channel?) {
        var brokerAddrFound: String? = null
        if (channel != null) {
            try {
                try {
                    lock.readLock().lockInterruptibly()
                    val itBrokerLiveTable: Iterator<Map.Entry<String?, BrokerLiveInfo>> =
                        brokerLiveTable!!.entries.iterator()
                    while (itBrokerLiveTable.hasNext()) {
                        val (key, value) = itBrokerLiveTable.next()
                        if (value.channel === channel) {
                            brokerAddrFound = key
                            break
                        }
                    }
                } finally {
                    lock.readLock().unlock()
                }
            } catch (e: Exception) {
                log.error("onChannelDestroy Exception", e)
            }
        }
        if (null == brokerAddrFound) {
            brokerAddrFound = remoteAddr
        } else {
            log.info("the broker's channel destroyed, {}, clean it's data structure at once", brokerAddrFound)
        }
        if (brokerAddrFound != null && brokerAddrFound.length > 0) {
            try {
                try {
                    lock.writeLock().lockInterruptibly()
                    brokerLiveTable!!.remove(brokerAddrFound)
                    filterServerTable!!.remove(brokerAddrFound)
                    var brokerNameFound: String? = null
                    var removeBrokerName = false
                    val itBrokerAddrTable: MutableIterator<Map.Entry<String, BrokerData?>> =
                        brokerAddrTable!!.entries.iterator()
                    while (itBrokerAddrTable.hasNext() && null == brokerNameFound) {
                        val brokerData: BrokerData? = itBrokerAddrTable.next().value
                        val it: MutableIterator<Map.Entry<Long, String>> = brokerData!!.brokerAddrs!!.iterator()
                        while (it.hasNext()) {
                            val (brokerId, brokerAddr) = it.next()
                            if (brokerAddr == brokerAddrFound) {
                                brokerNameFound = brokerData.brokerName
                                it.remove()
                                log.info(
                                    "remove brokerAddr[{}, {}] from brokerAddrTable, because channel destroyed",
                                    brokerId, brokerAddr
                                )
                                break
                            }
                        }
                        if (brokerData.brokerAddrs!!.isEmpty()) {
                            removeBrokerName = true
                            itBrokerAddrTable.remove()
                            log.info(
                                "remove brokerName[{}] from brokerAddrTable, because channel destroyed",
                                brokerData.brokerName
                            )
                        }
                    }
                    if (brokerNameFound != null && removeBrokerName) {
                        val it: MutableIterator<Map.Entry<String, MutableSet<String>>> =
                            clusterAddrTable!!.entries.iterator()
                        while (it.hasNext()) {
                            val (clusterName, brokerNames) = it.next()
                            val removed = brokerNames.remove(brokerNameFound)
                            if (removed) {
                                log.info(
                                    "remove brokerName[{}], clusterName[{}] from clusterAddrTable, because channel destroyed",
                                    brokerNameFound, clusterName
                                )
                                if (brokerNames.isEmpty()) {
                                    log.info(
                                        "remove the clusterName[{}] from clusterAddrTable, because channel destroyed and no broker in this cluster",
                                        clusterName
                                    )
                                    it.remove()
                                }
                                break
                            }
                        }
                    }
                    if (removeBrokerName) {
                        val itTopicQueueTable: MutableIterator<Map.Entry<String, MutableList<QueueData>?>> =
                            topicQueueTable!!.entries.iterator()
                        while (itTopicQueueTable.hasNext()) {
                            val (topic, queueDataList) = itTopicQueueTable.next()
                            val itQueueData: MutableIterator<QueueData> = queueDataList!!.iterator()
                            while (itQueueData.hasNext()) {
                                val queueData: QueueData = itQueueData.next()
                                if (queueData.brokerName.equals(brokerNameFound)) {
                                    itQueueData.remove()
                                    log.info(
                                        "remove topic[{} {}], from topicQueueTable, because channel destroyed",
                                        topic, queueData
                                    )
                                }
                            }
                            if (queueDataList.isEmpty()) {
                                itTopicQueueTable.remove()
                                log.info(
                                    "remove topic[{}] all queue, from topicQueueTable, because channel destroyed",
                                    topic
                                )
                            }
                        }
                    }
                } finally {
                    lock.writeLock().unlock()
                }
            } catch (e: Exception) {
                log.error("onChannelDestroy Exception", e)
            }
        }
    }

    fun printAllPeriodically() {
        try {
            try {
                lock.readLock().lockInterruptibly()
                log.info("--------------------------------------------------------")
                run {
                    log.info("topicQueueTable SIZE: {}", this.topicQueueTable!!.size)
                    val it: Iterator<Map.Entry<String, List<QueueData>?>> =
                        this.topicQueueTable!!.entries.iterator()
                    while (it.hasNext()) {
                        val (key, value) = it.next()
                        log.info("topicQueueTable Topic: {} {}", key, value)
                    }
                }
                run {
                    log.info("brokerAddrTable SIZE: {}", this.brokerAddrTable!!.size)
                    val it: Iterator<Map.Entry<String, BrokerData?>> =
                        this.brokerAddrTable!!.entries.iterator()
                    while (it.hasNext()) {
                        val (key, value) = it.next()
                        log.info("brokerAddrTable brokerName: {} {}", key, value)
                    }
                }
                run {
                    log.info("brokerLiveTable SIZE: {}", this.brokerLiveTable!!.size)
                    val it: Iterator<Map.Entry<String?, BrokerLiveInfo>> =
                        this.brokerLiveTable!!.entries.iterator()
                    while (it.hasNext()) {
                        val (key, value) = it.next()
                        log.info("brokerLiveTable brokerAddr: {} {}", key, value)
                    }
                }
                run {
                    log.info("clusterAddrTable SIZE: {}", this.clusterAddrTable!!.size)
                    val it: Iterator<Map.Entry<String, Set<String>>> =
                        this.clusterAddrTable!!.entries.iterator()
                    while (it.hasNext()) {
                        val (key, value) = it.next()
                        log.info("clusterAddrTable clusterName: {} {}", key, value)
                    }
                }
            } finally {
                lock.readLock().unlock()
            }
        } catch (e: Exception) {
            log.error("printAllPeriodically Exception", e)
        }
    }

    fun getSystemTopicList(): ByteArray? {
        val topicList = TopicList()
        try {
            try {
                lock.readLock().lockInterruptibly()
                for ((key, value) in clusterAddrTable!!) {
                    topicList.topicList.add(key)
                    topicList.topicList.addAll(value)
                }
                if (brokerAddrTable != null && !brokerAddrTable!!.isEmpty()) {
                    val it: Iterator<String> = brokerAddrTable!!.keys.iterator()
                    while (it.hasNext()) {
                        val bd: BrokerData? = brokerAddrTable!![it.next()]
                        val brokerAddrs: HashMap<Long, String> = bd!!.brokerAddrs!!
                        if (brokerAddrs != null && !brokerAddrs.isEmpty()) {
                            val it2: Iterator<Long> = brokerAddrs.keys.iterator()
                            topicList.brokerAddr = brokerAddrs[it2.next()]
                            break
                        }
                    }
                }
            } finally {
                lock.readLock().unlock()
            }
        } catch (e: Exception) {
            log.error("getAllTopicList Exception", e)
        }
        return topicList.encode()
    }

    fun getTopicsByCluster(cluster: String): ByteArray? {
        val topicList = TopicList()
        try {
            try {
                lock.readLock().lockInterruptibly()
                val brokerNameSet: Set<String> = clusterAddrTable!![cluster]!!
                for (brokerName in brokerNameSet) {
                    val topicTableIt: Iterator<Map.Entry<String, List<QueueData>?>> =
                        topicQueueTable!!.entries.iterator()
                    while (topicTableIt.hasNext()) {
                        val (topic, queueDatas) = topicTableIt.next()
                        for (queueData in queueDatas!!) {
                            if (brokerName == queueData.brokerName) {
                                topicList.topicList.add(topic)
                                break
                            }
                        }
                    }
                }
            } finally {
                lock.readLock().unlock()
            }
        } catch (e: Exception) {
            log.error("getAllTopicList Exception", e)
        }
        return topicList.encode()
    }

    fun getUnitTopics(): ByteArray? {
        val topicList = TopicList()
        try {
            try {
                lock.readLock().lockInterruptibly()
                val topicTableIt: Iterator<Map.Entry<String, List<QueueData>?>> = topicQueueTable!!.entries.iterator()
                while (topicTableIt.hasNext()) {
                    val (topic, queueDatas) = topicTableIt.next()
                    if (queueDatas != null && queueDatas.size > 0 && TopicSysFlag.hasUnitFlag(queueDatas[0].topicSynFlag)) {
                        topicList.topicList.add(topic)
                    }
                }
            } finally {
                lock.readLock().unlock()
            }
        } catch (e: Exception) {
            log.error("getAllTopicList Exception", e)
        }
        return topicList.encode()
    }

    fun getHasUnitSubTopicList(): ByteArray? {
        val topicList = TopicList()
        try {
            try {
                lock.readLock().lockInterruptibly()
                val topicTableIt: Iterator<Map.Entry<String, List<QueueData>?>> = topicQueueTable!!.entries.iterator()
                while (topicTableIt.hasNext()) {
                    val (topic, queueDatas) = topicTableIt.next()
                    if (queueDatas != null && queueDatas.size > 0 && TopicSysFlag.hasUnitSubFlag(queueDatas[0].topicSynFlag)) {
                        topicList.topicList.add(topic)
                    }
                }
            } finally {
                lock.readLock().unlock()
            }
        } catch (e: Exception) {
            log.error("getAllTopicList Exception", e)
        }
        return topicList.encode()
    }

    fun getHasUnitSubUnUnitTopicList(): ByteArray? {
        val topicList = TopicList()
        try {
            try {
                lock.readLock().lockInterruptibly()
                val topicTableIt: Iterator<Map.Entry<String, List<QueueData>?>> = topicQueueTable!!.entries.iterator()
                while (topicTableIt.hasNext()) {
                    val (topic, queueDatas) = topicTableIt.next()
                    if (queueDatas != null && queueDatas.size > 0 && !TopicSysFlag.hasUnitFlag(queueDatas[0].topicSynFlag)
                        && TopicSysFlag.hasUnitSubFlag(queueDatas[0].topicSynFlag)
                    ) {
                        topicList.topicList.add(topic)
                    }
                }
            } finally {
                lock.readLock().unlock()
            }
        } catch (e: Exception) {
            log.error("getAllTopicList Exception", e)
        }
        return topicList.encode()
    }
}