package com.agmtopy.kocketmq.remoting

interface RemotingService {
    /**
     * 开启服务
     */
    fun start()

    /**
     * 关闭服务
     */
    fun shutdown()

    /**
     * 注册RPC钩子
     */
    fun registerRPCHook(rpcHook: RPCHook?)

}