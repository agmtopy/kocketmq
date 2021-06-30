package com.agmtopy.kocketmq.common.protocol.header.namesrv

import com.agmtopy.kocketmq.remoting.CommandCustomHeader
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

class WipeWritePermOfBrokerResponseHeader : CommandCustomHeader {
    @CFNotNull
    var wipeTopicCount: Int? = null

    @Throws(RemotingCommandException::class)
    override fun checkFields() {
    }
}