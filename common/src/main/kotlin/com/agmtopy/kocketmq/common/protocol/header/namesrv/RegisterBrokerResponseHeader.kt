package com.agmtopy.kocketmq.common.protocol.header.namesrv

import com.agmtopy.kocketmq.remoting.CommandCustomHeader
import com.agmtopy.kocketmq.remoting.annotation.CFNullable
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

class RegisterBrokerResponseHeader : CommandCustomHeader {
    @CFNullable
    var haServerAddr: String? = null

    @CFNullable
    var masterAddr: String? = null

    @Throws(RemotingCommandException::class)
    override fun checkFields() {
    }
}