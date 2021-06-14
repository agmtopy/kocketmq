package com.agmtopy.kocketmq.remoting.exception.impl

import com.agmtopy.kocketmq.remoting.exception.RemotingException

/**
 * timeOut Exception
 */
class RemotingTimeoutException : RemotingException {
    private val serialVersionUID = 4106899185095245979L

    constructor (message: String?) {
        RemotingException(message)
    }

    constructor (addr: String, timeoutMillis: Long) {
        RemotingTimeoutException(addr, timeoutMillis, null)
    }

    constructor (addr: String, timeoutMillis: Long, cause: Throwable?) {
        RemotingException("wait response on the channel <$addr> timeout, $timeoutMillis(ms)", cause)
    }
}