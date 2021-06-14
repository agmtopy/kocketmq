package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.remoting.RemotingCommand

interface RemotingResponseCallback {
    fun callback(response: RemotingCommand?)
}