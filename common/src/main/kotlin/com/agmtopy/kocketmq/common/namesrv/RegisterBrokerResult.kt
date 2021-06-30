package com.agmtopy.kocketmq.common.namesrv

import com.agmtopy.kocketmq.common.protocol.body.KVTable

class RegisterBrokerResult {
    var haServerAddr: String? = null
    var masterAddr: String? = null
    var kvTable: KVTable? = null
}