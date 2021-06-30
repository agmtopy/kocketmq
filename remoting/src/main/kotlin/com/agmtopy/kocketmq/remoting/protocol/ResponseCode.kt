package com.agmtopy.kocketmq.remoting.protocol

class ResponseCode : RemotingSysResponseCode() {

    companion object {
        const val FLUSH_DISK_TIMEOUT = 10
        const val SLAVE_NOT_AVAILABLE = 11
        const val FLUSH_SLAVE_TIMEOUT = 12
        const val MESSAGE_ILLEGAL = 13
        const val SERVICE_NOT_AVAILABLE = 14
        const val VERSION_NOT_SUPPORTED = 15
        const val NO_PERMISSION = 16
        const val TOPIC_NOT_EXIST = 17
        const val TOPIC_EXIST_ALREADY = 18
        const val PULL_NOT_FOUND = 19
        const val PULL_RETRY_IMMEDIATELY = 20
        const val PULL_OFFSET_MOVED = 21
        const val QUERY_NOT_FOUND = 22
        const val SUBSCRIPTION_PARSE_FAILED = 23
        const val SUBSCRIPTION_NOT_EXIST = 24
        const val SUBSCRIPTION_NOT_LATEST = 25
        const val SUBSCRIPTION_GROUP_NOT_EXIST = 26
        const val FILTER_DATA_NOT_EXIST = 27
        const val FILTER_DATA_NOT_LATEST = 28
        const val TRANSACTION_SHOULD_COMMIT = 200
        const val TRANSACTION_SHOULD_ROLLBACK = 201
        const val TRANSACTION_STATE_UNKNOW = 202
        const val TRANSACTION_STATE_GROUP_WRONG = 203
        const val NO_BUYER_ID = 204
        const val NOT_IN_CURRENT_UNIT = 205
        const val CONSUMER_NOT_ONLINE = 206
        const val CONSUME_MSG_TIMEOUT = 207
        const val NO_MESSAGE = 208
        const val UPDATE_AND_CREATE_ACL_CONFIG_FAILED = 209
        const val DELETE_ACL_CONFIG_FAILED = 210
        const val UPDATE_GLOBAL_WHITE_ADDRS_CONFIG_FAILED = 211
    }
}