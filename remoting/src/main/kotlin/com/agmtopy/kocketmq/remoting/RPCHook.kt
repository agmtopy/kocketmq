package com.agmtopy.kocketmq.remoting

interface RPCHook {
    /**
     * RPC前置动作
     */
    fun doBeforeRequest(remoteAddr: String?, request: RemotingCommand?)

    /**
     * RPC后置动作
     */
    fun doAfterResponse(remoteAddr: String?, request: RemotingCommand?, response: RemotingCommand?)
}