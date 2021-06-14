package com.agmtopy.kocketmq.remoting.exception.impl

import com.agmtopy.kocketmq.remoting.exception.RemotingException

/**
 * 远程请求多数异常
 */
class RemotingTooMuchRequestException: RemotingException {
    private val serialVersionUID = 4326919581254519654L

    constructor (message: String?) {
        RemotingException(message)
    }
}