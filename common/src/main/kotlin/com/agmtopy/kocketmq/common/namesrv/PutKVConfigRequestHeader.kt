package com.agmtopy.kocketmq.common.namesrv

import com.agmtopy.kocketmq.remoting.CommandCustomHeader
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException

class PutKVConfigRequestHeader : CommandCustomHeader {
    @CFNotNull
    var namespace: String? = null

    @CFNotNull
    var key: String? = null

    @CFNotNull
    var value: String? = null

    @Throws(RemotingCommandException::class)
    override fun checkFields() {
    }
}