package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.broker.BrokerController
import com.agmtopy.kocketmq.common.TopicConfig
import com.agmtopy.kocketmq.common.constant.PermName
import com.agmtopy.kocketmq.common.constant.RequestCode
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.netty.NettyRequestProcessor
import com.agmtopy.kocketmq.remoting.protocol.ResponseCode
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.runBlocking

/**
 * 管理命令处理器
 *
 * 处理Broker的管理命令：
 * - 创建/更新Topic
 * - 查询Topic配置
 * - 查询Broker统计信息
 * - 查询运行时信息
 */
class AdminBrokerProcessor(
    private val brokerController: BrokerController
) : NettyRequestProcessor {

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(AdminBrokerProcessor::class.java)
    }

    override fun processRequest(ctx: ChannelHandlerContext, request: RemotingCommand): RemotingCommand {
        return when (request.code) {
            RequestCode.UPDATE_AND_CREATE_TOPIC -> updateAndCreateTopic(request)
            RequestCode.GET_ALL_TOPIC_CONFIG -> getAllTopicConfig(request)
            RequestCode.GET_BROKER_CONFIG -> getBrokerConfig(request)
            RequestCode.GET_BROKER_RUNTIME_INFO -> getBrokerRuntimeInfo(request)
            RequestCode.GET_MIN_OFFSET -> getMinOffset(request)
            RequestCode.GET_MAX_OFFSET -> getMaxOffset(request)
            else -> RemotingCommand.createResponseCommand(
                ResponseCode.REQUEST_CODE_NOT_SUPPORTED,
                "不支持此请求码: ${request.code}"
            )
        }
    }

    /**
     * 创建或更新Topic
     */
    private fun updateAndCreateTopic(request: RemotingCommand): RemotingCommand {
        return try {
            val topicConfig = decodeTopicConfig(request)

            if (topicConfig == null) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "解码Topic配置失败"
                )
            }

            // 验证Topic配置
            if (topicConfig.topicName.isBlank()) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "Topic名称不能为空"
                )
            }

            // 更新配置
            brokerController.topicConfigManager.updateTopicConfig(topicConfig)

            log.info("创建/更新Topic成功: {}", topicConfig.topicName)

            RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")

        } catch (e: Exception) {
            log.error("创建/更新Topic失败", e)
            RemotingCommand.createResponseCommand(
                ResponseCode.SYSTEM_ERROR,
                "操作失败: ${e.message}"
            )
        }
    }

    /**
     * 获取所有Topic配置
     */
    private fun getAllTopicConfig(request: RemotingCommand): RemotingCommand {
        return try {
            val allTopicConfig = brokerController.topicConfigManager.getAllTopicConfig()

            // TODO: 序列化为JSON
            val topicList = allTopicConfig.keys.joinToString(",")

            val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
            response.body = topicList.toByteArray()

            response

        } catch (e: Exception) {
            log.error("获取Topic配置失败", e)
            RemotingCommand.createResponseCommand(
                ResponseCode.SYSTEM_ERROR,
                "操作失败: ${e.message}"
            )
        }
    }

    /**
     * 获取Broker配置
     */
    private fun getBrokerConfig(request: RemotingCommand): RemotingCommand {
        return try {
            val config = brokerController.brokerConfig

            // TODO: 序列化为JSON
            val configStr = """
                brokerName=${config.brokerName}
                brokerId=${config.brokerId}
                clusterName=${config.clusterName}
                listenPort=${config.listenPort}
                storePathRootDir=${config.storePathRootDir}
            """.trimIndent()

            val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
            response.body = configStr.toByteArray()

            response

        } catch (e: Exception) {
            log.error("获取Broker配置失败", e)
            RemotingCommand.createResponseCommand(
                ResponseCode.SYSTEM_ERROR,
                "操作失败: ${e.message}"
            )
        }
    }

    /**
     * 获取Broker运行时信息
     */
    private fun getBrokerRuntimeInfo(request: RemotingCommand): RemotingCommand {
        return try {
            val runtimeInfo = runBlocking {
                mapOf(
                    "brokerName" to brokerController.getBrokerName(),
                    "brokerId" to brokerController.getBrokerId().toString(),
                    "clusterName" to brokerController.getClusterName(),
                    "listenPort" to brokerController.getListenPort().toString(),
                    "msgTotal" to "0",  // TODO: 统计消息总数
                    "msgPutTotal" to "0",  // TODO: 统计发送总数
                    "msgGetTotal" to "0",  // TODO: 统计拉取总数
                    "commitLogSize" to brokerController.messageStore.getMaxOffset().toString(),
                    "commitLogCount" to brokerController.messageStore.getCommitLogFileCount().toString(),
                    "consumeQueueCount" to brokerController.messageStore.getConsumeQueueCount().toString(),
                    "topicCount" to brokerController.topicConfigManager.getTopicCount().toString(),
                    "runtime" to Runtime.getRuntime().let {
                        "maxMemory=${it.maxMemory()},totalMemory=${it.totalMemory()},freeMemory=${it.freeMemory()}"
                    }
                )
            }

            // TODO: 序列化为JSON
            val infoStr = runtimeInfo.entries.joinToString("\n") { "${it.key}=${it.value}" }

            val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
            response.body = infoStr.toByteArray()

            response

        } catch (e: Exception) {
            log.error("获取运行时信息失败", e)
            RemotingCommand.createResponseCommand(
                ResponseCode.SYSTEM_ERROR,
                "操作失败: ${e.message}"
            )
        }
    }

    /**
     * 获取最小offset
     */
    private fun getMinOffset(request: RemotingCommand): RemotingCommand {
        return try {
            val topic = request.extFields?.get("topic")

            if (topic.isNullOrBlank()) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "Topic不能为空"
                )
            }

            val minOffset = runBlocking {
                brokerController.messageStore.getMinOffset()
            }

            val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
            response.setExtFields(mapOf("offset" to minOffset.toString()))

            response

        } catch (e: Exception) {
            log.error("获取最小offset失败", e)
            RemotingCommand.createResponseCommand(
                ResponseCode.SYSTEM_ERROR,
                "操作失败: ${e.message}"
            )
        }
    }

    /**
     * 获取最大offset
     */
    private fun getMaxOffset(request: RemotingCommand): RemotingCommand {
        return try {
            val topic = request.extFields?.get("topic")

            if (topic.isNullOrBlank()) {
                return RemotingCommand.createResponseCommand(
                    ResponseCode.SYSTEM_ERROR,
                    "Topic不能为空"
                )
            }

            val maxOffset = runBlocking {
                brokerController.messageStore.getMaxOffset()
            }

            val response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, "OK")
            response.setExtFields(mapOf("offset" to maxOffset.toString()))

            response

        } catch (e: Exception) {
            log.error("获取最大offset失败", e)
            RemotingCommand.createResponseCommand(
                ResponseCode.SYSTEM_ERROR,
                "操作失败: ${e.message}"
            )
        }
    }

    /**
     * 解码Topic配置
     */
    private fun decodeTopicConfig(request: RemotingCommand): TopicConfig? {
        return try {
            val extFields = request.extFields ?: return null

            TopicConfig(
                topicName = extFields["topicName"] ?: "",
                readQueueNums = extFields["readQueueNums"]?.toInt() ?: 8,
                writeQueueNums = extFields["writeQueueNums"]?.toInt() ?: 8,
                perm = extFields["perm"]?.toInt() ?: (PermName.PERM_READ or PermName.PERM_WRITE),
                topicFilterType = com.agmtopy.kocketmq.common.enum.TopicFilterType.SINGLE_TAG
            )
        } catch (e: Exception) {
            log.error("解码Topic配置失败", e)
            null
        }
    }
}
