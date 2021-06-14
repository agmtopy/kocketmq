package com.agmtopy.kocketmq.remoting.exception.impl

import com.agmtopy.kocketmq.remoting.exception.RemotingException

/**
 * 定义发生请求时Exception
 */
class RemotingSendRequestException: RemotingException {

    private val serialVersionUID = 5391285827332471674L

    constructor(addr: String?) {
        RemotingSendRequestException(addr, null)
    }

    constructor(addr: String?, cause: Throwable?) {
        RemotingException("send request to <$addr> failed", cause)
    }
}