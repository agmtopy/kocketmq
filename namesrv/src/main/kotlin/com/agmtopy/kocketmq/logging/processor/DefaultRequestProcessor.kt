package com.agmtopy.kocketmq.logging.processor

import com.agmtopy.kocketmq.common.DataVersion
import com.agmtopy.kocketmq.common.MQVersion
import com.agmtopy.kocketmq.common.MixAll
import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.common.namesrv.*
import com.agmtopy.kocketmq.common.protocol.body.RegisterBrokerBody
import com.agmtopy.kocketmq.common.protocol.body.TopicConfigSerializeWrapper
import com.agmtopy.kocketmq.common.protocol.header.GetTopicsByClusterRequestHeader
import com.agmtopy.kocketmq.common.protocol.header.namesrv.*
import com.agmtopy.kocketmq.common.protocol.route.TopicRouteData
import com.agmtopy.kocketmq.common.util.FAQUrl
import com.agmtopy.kocketmq.common.util.UtilAll
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.NamesrvController
import com.agmtopy.kocketmq.logging.constant.NamesrvUtil
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException
import com.agmtopy.kocketmq.remoting.netty.AsyncNettyRequestProcessor
import com.agmtopy.kocketmq.remoting.netty.NettyRequestProcessor
import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import com.agmtopy.kocketmq.remoting.protocol.RemotingSysResponseCode
import com.agmtopy.kocketmq.remoting.protocol.ResponseCode
import io.netty.channel.ChannelHandlerContext
import java.io.UnsupportedEncodingException
import java.util.*
import java.util.concurrent.atomic.AtomicLong

open class DefaultRequestProcessor(namesrvController: NamesrvController) : AsyncNettyRequestProcessor(),
    NettyRequestProcessor {
    protected val namesrvController: NamesrvController

    @Throws(RemotingCommandException::class)
    override fun processRequest(ctx: ChannelHandlerContext?, request: RemotingCommand?): RemotingCommand? {
        request!!
        if (ctx != null) {
            log.debug(
                "receive request, $request.code $RemotingHelper.parseChannelRemoteAddr(ctx.channel()) $request"
            )
        }
        when (request.code) {
            RequestCode.PUT_KV_CONFIG -> return putKVConfig(ctx, request)
            RequestCode.GET_KV_CONFIG -> return getKVConfig(ctx, request)
            RequestCode.DELETE_KV_CONFIG -> return deleteKVConfig(ctx, request)
            RequestCode.QUERY_DATA_VERSION -> return queryBrokerTopicConfig(ctx, request)
            RequestCode.REGISTER_BROKER -> {
                val brokerVersion: MQVersion.Version = MQVersion.value2Version(request.version)
                return if (brokerVersion.ordinal >= MQVersion.Version.V3_0_11.ordinal) {
                    registerBrokerWithFilterServer(ctx, request)
                } else {
                    registerBroker(ctx, request)
                }
            }
            RequestCode.UNREGISTER_BROKER -> return unregisterBroker(ctx, request)
            RequestCode.GET_ROUTEINFO_BY_TOPIC -> return getRouteInfoByTopic(ctx, request)
            RequestCode.GET_BROKER_CLUSTER_INFO -> return getBrokerClusterInfo(ctx, request)
            RequestCode.WIPE_WRITE_PERM_OF_BROKER -> return wipeWritePermOfBroker(ctx, request)
            RequestCode.GET_ALL_TOPIC_LIST_FROM_NAMESERVER -> return getAllTopicListFromNameserver(ctx, request)
            RequestCode.DELETE_TOPIC_IN_NAMESRV -> return deleteTopicInNamesrv(ctx, request)
            RequestCode.GET_KVLIST_BY_NAMESPACE -> return getKVListByNamespace(ctx, request)
            RequestCode.GET_TOPICS_BY_CLUSTER -> return getTopicsByCluster(ctx, request)
            RequestCode.GET_SYSTEM_TOPIC_LIST_FROM_NS -> return getSystemTopicListFromNs(ctx, request)
            RequestCode.GET_UNIT_TOPIC_LIST -> return getUnitTopicList(ctx, request)
            RequestCode.GET_HAS_UNIT_SUB_TOPIC_LIST -> return getHasUnitSubTopicList(ctx, request)
            RequestCode.GET_HAS_UNIT_SUB_UNUNIT_TOPIC_LIST -> return getHasUnitSubUnUnitTopicList(ctx, request)
            RequestCode.UPDATE_NAMESRV_CONFIG -> return updateConfig(ctx, request)
            RequestCode.GET_NAMESRV_CONFIG -> return getConfig(ctx, request)
            else -> {
            }
        }
        return null
    }

    override fun rejectRequest(): Boolean {
        return false
    }

    @Throws(RemotingCommandException::class)
    fun putKVConfig(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val requestHeader: PutKVConfigRequestHeader =
            request.decodeCommandCustomHeader(PutKVConfigRequestHeader::class.java) as PutKVConfigRequestHeader
        namesrvController.kvConfigManager.putKVConfig(
            requestHeader.namespace,
            requestHeader.key,
            requestHeader.value
        )
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    fun getKVConfig(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(GetKVConfigResponseHeader::class.java)!!
        val responseHeader: GetKVConfigResponseHeader = response.readCustomHeader() as GetKVConfigResponseHeader
        val requestHeader: GetKVConfigRequestHeader =
            request.decodeCommandCustomHeader(GetKVConfigRequestHeader::class.java) as GetKVConfigRequestHeader
        val value: String? = namesrvController.kvConfigManager.getKVConfig(
            requestHeader.namespace,
            requestHeader.key
        )
        if (value != null) {
            responseHeader.value = value
            response.code = RemotingSysResponseCode.SUCCESS
            response.remark = (null)
            return response
        }
        response.code = ResponseCode.QUERY_NOT_FOUND
        response.remark = (
                "No config item, Namespace: " + requestHeader.namespace
                    .toString() + " Key: " + requestHeader.key
                )
        return response
    }

    @Throws(RemotingCommandException::class)
    fun deleteKVConfig(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val requestHeader: DeleteKVConfigRequestHeader = request.decodeCommandCustomHeader(
            DeleteKVConfigRequestHeader::class.java
        ) as DeleteKVConfigRequestHeader
        namesrvController.kvConfigManager.deleteKVConfig(
            requestHeader.namespace,
            requestHeader.key
        )
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    fun registerBrokerWithFilterServer(ctx: ChannelHandlerContext?, request: RemotingCommand): RemotingCommand {
        val response: RemotingCommand =
            RemotingCommand.createResponseCommand(RegisterBrokerResponseHeader::class.java)!!
        val responseHeader: RegisterBrokerResponseHeader = response.readCustomHeader() as RegisterBrokerResponseHeader
        val requestHeader: RegisterBrokerRequestHeader = request.decodeCommandCustomHeader(
            RegisterBrokerRequestHeader::class.java
        ) as RegisterBrokerRequestHeader
        if (!checksum(ctx, request, requestHeader)) {
            response.code = RemotingSysResponseCode.SYSTEM_ERROR
            response.remark = ("crc32 not match")
            return response
        }
        var registerBrokerBody = RegisterBrokerBody()
        if (request.getBody() != null) {
            registerBrokerBody = try {
                RegisterBrokerBody.decode(request.getBody(), requestHeader.isCompressed)
            } catch (e: Exception) {
                throw RemotingCommandException("Failed to decode RegisterBrokerBody", e)
            }
        } else {
            registerBrokerBody.topicConfigSerializeWrapper.dataVersion.setCounter(AtomicLong(0))
            registerBrokerBody.topicConfigSerializeWrapper.dataVersion.setTimestamp(0)
        }
        val result: RegisterBrokerResult? = namesrvController.routeInfoManager.registerBroker(
            requestHeader.clusterName!!,
            requestHeader.brokerAddr,
            requestHeader.brokerName!!,
            requestHeader.brokerId!!,
            requestHeader.haServerAddr,
            registerBrokerBody.topicConfigSerializeWrapper,
            registerBrokerBody.filterServerList,
            ctx!!.channel()
        )
        responseHeader.haServerAddr = result!!.haServerAddr
        responseHeader.masterAddr = result!!.masterAddr
        val jsonValue: ByteArray? =
            namesrvController.kvConfigManager.getKVListByNamespace(NamesrvUtil.NAMESPACE_ORDER_TOPIC_CONFIG)
        response.setBody(jsonValue)
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    private fun checksum(
        ctx: ChannelHandlerContext?, request: RemotingCommand,
        requestHeader: RegisterBrokerRequestHeader
    ): Boolean {
        if (requestHeader.bodyCrc32 !== 0) {
            val crc32: Int = UtilAll.crc32(request.getBody())
            if (crc32 != requestHeader.bodyCrc32) {
                log.warn(
                    java.lang.String.format(
                        "receive registerBroker request,crc32 not match,from %s",
                        RemotingHelper.parseChannelRemoteAddr(ctx!!.channel())
                    )
                )
                return false
            }
        }
        return true
    }

    @Throws(RemotingCommandException::class)
    fun queryBrokerTopicConfig(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand =
            RemotingCommand.createResponseCommand(QueryDataVersionResponseHeader::class.java)!!
        val responseHeader: QueryDataVersionResponseHeader =
            response.readCustomHeader() as QueryDataVersionResponseHeader
        val requestHeader: QueryDataVersionRequestHeader = request.decodeCommandCustomHeader(
            QueryDataVersionRequestHeader::class.java
        ) as QueryDataVersionRequestHeader
        val dataVersion: DataVersion = RemotingSerializable.decode(request.getBody(), DataVersion::class.java)
        val changed: Boolean = namesrvController.routeInfoManager
            .isBrokerTopicConfigChanged(requestHeader.brokerAddr, dataVersion)
        if (!changed) {
            namesrvController.routeInfoManager.updateBrokerInfoUpdateTimestamp(requestHeader.brokerAddr)
        }
        val nameSeverDataVersion: DataVersion? =
            namesrvController.routeInfoManager.queryBrokerTopicConfig(requestHeader.brokerAddr)
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        if (nameSeverDataVersion != null) {
            response.setBody(nameSeverDataVersion.encode())
        }
        responseHeader.changed = changed
        return response
    }

    @Throws(RemotingCommandException::class)
    fun registerBroker(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand =
            RemotingCommand.createResponseCommand(RegisterBrokerResponseHeader::class.java)!!
        val responseHeader: RegisterBrokerResponseHeader = response.readCustomHeader() as RegisterBrokerResponseHeader
        val requestHeader: RegisterBrokerRequestHeader = request.decodeCommandCustomHeader(
            RegisterBrokerRequestHeader::class.java
        ) as RegisterBrokerRequestHeader
        if (!checksum(ctx, request, requestHeader)) {
            response.code = RemotingSysResponseCode.SYSTEM_ERROR
            response.remark = ("crc32 not match")
            return response
        }
        val topicConfigWrapper: TopicConfigSerializeWrapper
        if (request.getBody() != null) {
            topicConfigWrapper =
                TopicConfigSerializeWrapper@ RemotingSerializable.decode(
                    request.getBody(),
                    TopicConfigSerializeWrapper::class.java
                )
        } else {
            topicConfigWrapper = TopicConfigSerializeWrapper()
            topicConfigWrapper.dataVersion.setCounter(AtomicLong(0))
            topicConfigWrapper.dataVersion.setTimestamp(0)
        }
        val result: RegisterBrokerResult? = namesrvController.routeInfoManager.registerBroker(
            requestHeader.clusterName!!,
            requestHeader.brokerAddr,
            requestHeader.brokerName!!,
            requestHeader.brokerId!!,
            requestHeader.haServerAddr,
            topicConfigWrapper,
            null,
            ctx!!.channel()
        )
        responseHeader.haServerAddr = result!!.haServerAddr
        responseHeader.masterAddr = result!!.masterAddr
        val jsonValue: ByteArray? =
            namesrvController.kvConfigManager.getKVListByNamespace(NamesrvUtil.NAMESPACE_ORDER_TOPIC_CONFIG)
        response.setBody(jsonValue)
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    fun unregisterBroker(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val requestHeader: UnRegisterBrokerRequestHeader = request.decodeCommandCustomHeader(
            UnRegisterBrokerRequestHeader::class.java
        ) as UnRegisterBrokerRequestHeader
        namesrvController.routeInfoManager.unregisterBroker(
            requestHeader.clusterName!!,
            requestHeader.brokerAddr,
            requestHeader.brokerName!!,
            requestHeader.brokerId!!
        )
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    fun getRouteInfoByTopic(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val requestHeader: GetRouteInfoRequestHeader =
            request.decodeCommandCustomHeader(GetRouteInfoRequestHeader::class.java) as GetRouteInfoRequestHeader
        val topicRouteData: TopicRouteData? =
            namesrvController.routeInfoManager.pickupTopicRouteData(requestHeader.topic!!)
        if (topicRouteData != null) {
            if (namesrvController.namesrvConfig.isOrderMessageEnable()) {
                val orderTopicConf: String? = namesrvController.kvConfigManager.getKVConfig(
                    NamesrvUtil.NAMESPACE_ORDER_TOPIC_CONFIG,
                    requestHeader.topic
                )
                topicRouteData.orderTopicConf = orderTopicConf
            }
            val content: ByteArray = topicRouteData.encode()!!
            response.setBody(content)
            response.code = RemotingSysResponseCode.SUCCESS
            response.remark = (null)
            return response
        }
        response.code = ResponseCode.TOPIC_NOT_EXIST
        response.remark = (
                "No topic route info in name server for the topic: " + requestHeader.topic
                        + FAQUrl.suggestTodo(FAQUrl.APPLY_TOPIC_URL)
                )
        return response
    }

    private fun getBrokerClusterInfo(ctx: ChannelHandlerContext?, request: RemotingCommand): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val content: ByteArray? = namesrvController.routeInfoManager.getAllClusterInfo()
        response.setBody(content)
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    private fun wipeWritePermOfBroker(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand =
            RemotingCommand.createResponseCommand(WipeWritePermOfBrokerResponseHeader::class.java)!!
        val responseHeader: WipeWritePermOfBrokerResponseHeader =
            response.readCustomHeader() as WipeWritePermOfBrokerResponseHeader
        val requestHeader: WipeWritePermOfBrokerRequestHeader = request.decodeCommandCustomHeader(
            WipeWritePermOfBrokerRequestHeader::class.java
        ) as WipeWritePermOfBrokerRequestHeader
        val wipeTopicCnt: Int =
            namesrvController.routeInfoManager.wipeWritePermOfBrokerByLock(requestHeader.brokerName!!)
        if (ctx != null) {
            log.info(
                "wipe write perm of broker[$requestHeader.getBrokerName()], client: $RemotingHelper.parseChannelRemoteAddr(ctx.channel()), $wipeTopicCnt",
            )
        }
        responseHeader.wipeTopicCount = wipeTopicCnt
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    private fun getAllTopicListFromNameserver(ctx: ChannelHandlerContext?, request: RemotingCommand): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val body: ByteArray = namesrvController.routeInfoManager.getAllTopicList()!!
        response.setBody(body)
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    private fun deleteTopicInNamesrv(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val requestHeader: DeleteTopicInNamesrvRequestHeader = request.decodeCommandCustomHeader(
            DeleteTopicInNamesrvRequestHeader::class.java
        ) as DeleteTopicInNamesrvRequestHeader
        namesrvController.routeInfoManager.deleteTopic(requestHeader.topic!!)
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    private fun getKVListByNamespace(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val requestHeader: GetKVListByNamespaceRequestHeader = request.decodeCommandCustomHeader(
            GetKVListByNamespaceRequestHeader::class.java
        ) as GetKVListByNamespaceRequestHeader
        val jsonValue: ByteArray = namesrvController.kvConfigManager.getKVListByNamespace(
            requestHeader.namespace
        )!!
        if (null != jsonValue) {
            response.setBody(jsonValue)
            response.code = RemotingSysResponseCode.SUCCESS
            response.remark = (null)
            return response
        }
        response.code = ResponseCode.QUERY_NOT_FOUND
        response.remark = ("No config item, Namespace: " + requestHeader.namespace)
        return response
    }

    @Throws(RemotingCommandException::class)
    private fun getTopicsByCluster(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand? = RemotingCommand.createResponseCommand(null)
        val requestHeader: GetTopicsByClusterRequestHeader = request.decodeCommandCustomHeader(
            GetTopicsByClusterRequestHeader::class.java
        ) as GetTopicsByClusterRequestHeader
        val body: ByteArray = namesrvController.routeInfoManager.getTopicsByCluster(requestHeader.cluster!!)!!
        response!!.setBody(body)
        response!!.code = RemotingSysResponseCode.SUCCESS
        response!!.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    private fun getSystemTopicListFromNs(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand? = RemotingCommand.createResponseCommand(null)
        val body: ByteArray = namesrvController.routeInfoManager.getSystemTopicList()!!
        response!!.setBody(body)
        response!!.code = RemotingSysResponseCode.SUCCESS
        response!!.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    private fun getUnitTopicList(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand? = RemotingCommand.createResponseCommand(null)
        val body: ByteArray = namesrvController.routeInfoManager.getUnitTopics()!!
        response!!.setBody(body)
        response!!.code = RemotingSysResponseCode.SUCCESS
        response!!.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    private fun getHasUnitSubTopicList(
        ctx: ChannelHandlerContext?,
        request: RemotingCommand
    ): RemotingCommand {
        val response: RemotingCommand? = RemotingCommand.createResponseCommand(null)
        val body: ByteArray = namesrvController.routeInfoManager.getHasUnitSubTopicList()!!
        response!!.setBody(body)
        response!!.code = RemotingSysResponseCode.SUCCESS
        response!!.remark = (null)
        return response
    }

    @Throws(RemotingCommandException::class)
    private fun getHasUnitSubUnUnitTopicList(ctx: ChannelHandlerContext?, request: RemotingCommand): RemotingCommand {
        val response: RemotingCommand? = RemotingCommand.createResponseCommand(null)
        val body: ByteArray = namesrvController.routeInfoManager.getHasUnitSubUnUnitTopicList()!!
        response!!.setBody(body)
        response!!.code = RemotingSysResponseCode.SUCCESS
        response!!.remark = (null)
        return response
    }

    private fun updateConfig(ctx: ChannelHandlerContext?, request: RemotingCommand): RemotingCommand {
        if (ctx != null) {
            log.info("updateConfig called by {}", RemotingHelper.parseChannelRemoteAddr(ctx.channel()))
        }
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val body: ByteArray = request.getBody()!!
        if (body != null) {
            val bodyStr: java.lang.String = java.lang.String(body, MixAll.DEFAULT_CHARSET)
            val properties: Properties = MixAll.string2Properties(String(bodyStr.bytes))!!
            if (properties == null) {
                log.error("updateConfig MixAll.string2Properties error {}", bodyStr)
                response.code = RemotingSysResponseCode.SYSTEM_ERROR
                response.remark = "string2Properties error"
                return response
            }
            namesrvController.configuration.update(properties)
        }
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = null
        return response
    }

    private fun getConfig(ctx: ChannelHandlerContext?, request: RemotingCommand): RemotingCommand {
        val response: RemotingCommand = RemotingCommand.createResponseCommand(null)!!
        val content: String? = namesrvController.configuration.getAllConfigsFormatString()
        if (content != null && content.length > 0) {
            try {
                response.setBody(content.toByteArray())
            } catch (e: UnsupportedEncodingException) {
                log.error("getConfig error, ", e)
                response.code = RemotingSysResponseCode.SYSTEM_ERROR
                response.remark = "UnsupportedEncodingException $e"
                return response
            }
        }
        response.code = RemotingSysResponseCode.SUCCESS
        response.remark = null
        return response
    }

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME)
    }

    init {
        this.namesrvController = namesrvController
    }
}
