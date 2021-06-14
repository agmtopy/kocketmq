package com.agmtopy.kocketmq.remoting

import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

/**
 * 自定义命令处理器
 */
interface CommandCustomHeader {
    @Throws(RemotingCommandException::class)
    fun checkFields()
}