package com.agmtopy.kocketmq.common.protocol.header

import com.agmtopy.kocketmq.remoting.CommandCustomHeader
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

class GetTopicsByClusterRequestHeader : CommandCustomHeader {
    @CFNotNull
    var cluster: String? = null

    @Throws(RemotingCommandException::class)
    override fun checkFields() {
    }
}