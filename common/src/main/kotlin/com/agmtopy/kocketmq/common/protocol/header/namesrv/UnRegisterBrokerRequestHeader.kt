package com.agmtopy.kocketmq.common.protocol.header.namesrv

import com.agmtopy.kocketmq.remoting.CommandCustomHeader
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

class UnRegisterBrokerRequestHeader : CommandCustomHeader {
    @CFNotNull
    var brokerName: String? = null

    @CFNotNull
    var brokerAddr: String? = null

    @CFNotNull
    var clusterName: String? = null

    @CFNotNull
    var brokerId: Long? = null

    @Throws(RemotingCommandException::class)
    override fun checkFields() {
    }
}