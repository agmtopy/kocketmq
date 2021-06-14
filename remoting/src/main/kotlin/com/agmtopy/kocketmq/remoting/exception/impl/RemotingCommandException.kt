package com.agmtopy.kocketmq.remoting.exception.impl

import com.agmtopy.kocketmq.remoting.exception.RemotingException

class RemotingCommandException : RemotingException {
    constructor(message: String?) : super(message, null) {}
    constructor(message: String?, cause: Throwable?) : super(message, cause) {}

    companion object {
        private const val serialVersionUID = -6061365915274953096L
    }
}