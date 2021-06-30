package com.agmtopy.kocketmq.common.protocol.header.namesrv

import com.agmtopy.kocketmq.remoting.CommandCustomHeader
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

class RegisterBrokerRequestHeader : CommandCustomHeader {
    @CFNotNull
    var brokerName: String? = null

    @CFNotNull
    var brokerAddr: String? = null

    @CFNotNull
    var clusterName: String? = null

    @CFNotNull
    var haServerAddr: String? = null

    @CFNotNull
    var brokerId: Long? = null
    var isCompressed = false
    var bodyCrc32 = 0

    @Throws(RemotingCommandException::class)
    override fun checkFields() {
    }
}