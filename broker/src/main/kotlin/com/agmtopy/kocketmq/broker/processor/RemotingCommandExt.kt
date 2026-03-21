package com.agmtopy.kocketmq.broker.processor

import com.agmtopy.kocketmq.remoting.RemotingCommand

/**
 * 批量添加扩展字段
 */
fun RemotingCommand?.setExtFields(fields: Map<String, String>) {
    this?.let { cmd ->
        fields.forEach { (key, value) ->
            cmd.addExtField(key, value)
        }
    }
}
