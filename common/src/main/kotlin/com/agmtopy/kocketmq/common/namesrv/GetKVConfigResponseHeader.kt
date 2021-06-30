package com.agmtopy.kocketmq.common.namesrv

import com.agmtopy.kocketmq.remoting.CommandCustomHeader
import com.agmtopy.kocketmq.remoting.annotation.CFNullable
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

class GetKVConfigResponseHeader : CommandCustomHeader {
    @CFNullable
    var value: String? = null

    @Throws(RemotingCommandException::class)
    override fun checkFields() {
    }
}
