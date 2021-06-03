package com.agmtopy.kocketmq.remoting

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.protocol.SerializeType


/**
 * 远程命令
 */
class RemotingCommand{
    companion object{
        //静态变量区
        const val SERIALIZE_TYPE_PROPERTY = "rocketmq.serialize.type"
        const val SERIALIZE_TYPE_ENV = "ROCKETMQ_SERIALIZE_TYPE"
        const val REMOTING_VERSION_KEY = "rocketmq.remoting.version"
        private val log: InternalLogger? = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)

        //序列化方式
        private val serializeTypeConfigInThisServer = SerializeType.JSON


        //静态方法区
        open fun getSerializeTypeConfigInThisServer(): SerializeType? {
            return serializeTypeConfigInThisServer
        }
    }
}