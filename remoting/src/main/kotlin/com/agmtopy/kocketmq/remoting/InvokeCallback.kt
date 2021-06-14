package com.agmtopy.kocketmq.remoting

import com.agmtopy.kocketmq.remoting.netty.ResponseFuture

interface InvokeCallback {
    fun operationComplete(responseFuture: ResponseFuture?)
}