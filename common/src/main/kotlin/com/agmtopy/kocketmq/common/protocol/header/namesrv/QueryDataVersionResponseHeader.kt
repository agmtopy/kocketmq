package com.agmtopy.kocketmq.common.protocol.header.namesrv

import com.agmtopy.kocketmq.remoting.CommandCustomHeader
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

class QueryDataVersionResponseHeader : CommandCustomHeader {
    @CFNotNull
    var changed: Boolean? = null

    @Throws(RemotingCommandException::class)
    override fun checkFields() {
    }

    override fun toString(): String {
        val sb = StringBuilder("QueryDataVersionResponseHeader{")
        sb.append("changed=").append(changed)
        sb.append('}')
        return sb.toString()
    }
}