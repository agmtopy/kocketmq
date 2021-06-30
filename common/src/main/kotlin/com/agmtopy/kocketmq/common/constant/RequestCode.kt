package com.agmtopy.kocketmq.common.constant

object RequestCode {
    const val SEND_MESSAGE = 10
    const val PULL_MESSAGE = 11
    const val QUERY_MESSAGE = 12
    const val QUERY_BROKER_OFFSET = 13
    const val QUERY_CONSUMER_OFFSET = 14
    const val UPDATE_CONSUMER_OFFSET = 15
    const val UPDATE_AND_CREATE_TOPIC = 17
    const val GET_ALL_TOPIC_CONFIG = 21
    const val GET_TOPIC_CONFIG_LIST = 22
    const val GET_TOPIC_NAME_LIST = 23
    const val UPDATE_BROKER_CONFIG = 25
    const val GET_BROKER_CONFIG = 26
    const val TRIGGER_DELETE_FILES = 27
    const val GET_BROKER_RUNTIME_INFO = 28
    const val SEARCH_OFFSET_BY_TIMESTAMP = 29
    const val GET_MAX_OFFSET = 30
    const val GET_MIN_OFFSET = 31
    const val GET_EARLIEST_MSG_STORETIME = 32
    const val VIEW_MESSAGE_BY_ID = 33
    const val HEART_BEAT = 34
    const val UNREGISTER_CLIENT = 35
    const val CONSUMER_SEND_MSG_BACK = 36
    const val END_TRANSACTION = 37
    const val GET_CONSUMER_LIST_BY_GROUP = 38
    const val CHECK_TRANSACTION_STATE = 39
    const val NOTIFY_CONSUMER_IDS_CHANGED = 40
    const val LOCK_BATCH_MQ = 41
    const val UNLOCK_BATCH_MQ = 42
    const val GET_ALL_CONSUMER_OFFSET = 43
    const val GET_ALL_DELAY_OFFSET = 45
    const val CHECK_CLIENT_CONFIG = 46
    const val UPDATE_AND_CREATE_ACL_CONFIG = 50
    const val DELETE_ACL_CONFIG = 51
    const val GET_BROKER_CLUSTER_ACL_INFO = 52
    const val UPDATE_GLOBAL_WHITE_ADDRS_CONFIG = 53
    const val GET_BROKER_CLUSTER_ACL_CONFIG = 54
    const val PUT_KV_CONFIG = 100
    const val GET_KV_CONFIG = 101
    const val DELETE_KV_CONFIG = 102
    const val REGISTER_BROKER = 103
    const val UNREGISTER_BROKER = 104
    const val GET_ROUTEINFO_BY_TOPIC = 105
    const val GET_BROKER_CLUSTER_INFO = 106
    const val UPDATE_AND_CREATE_SUBSCRIPTIONGROUP = 200
    const val GET_ALL_SUBSCRIPTIONGROUP_CONFIG = 201
    const val GET_TOPIC_STATS_INFO = 202
    const val GET_CONSUMER_CONNECTION_LIST = 203
    const val GET_PRODUCER_CONNECTION_LIST = 204
    const val WIPE_WRITE_PERM_OF_BROKER = 205
    const val GET_ALL_TOPIC_LIST_FROM_NAMESERVER = 206
    const val DELETE_SUBSCRIPTIONGROUP = 207
    const val GET_CONSUME_STATS = 208
    const val SUSPEND_CONSUMER = 209
    const val RESUME_CONSUMER = 210
    const val RESET_CONSUMER_OFFSET_IN_CONSUMER = 211
    const val RESET_CONSUMER_OFFSET_IN_BROKER = 212
    const val ADJUST_CONSUMER_THREAD_POOL = 213
    const val WHO_CONSUME_THE_MESSAGE = 214
    const val DELETE_TOPIC_IN_BROKER = 215
    const val DELETE_TOPIC_IN_NAMESRV = 216
    const val GET_KVLIST_BY_NAMESPACE = 219
    const val RESET_CONSUMER_CLIENT_OFFSET = 220
    const val GET_CONSUMER_STATUS_FROM_CLIENT = 221
    const val INVOKE_BROKER_TO_RESET_OFFSET = 222
    const val INVOKE_BROKER_TO_GET_CONSUMER_STATUS = 223
    const val QUERY_TOPIC_CONSUME_BY_WHO = 300
    const val GET_TOPICS_BY_CLUSTER = 224
    const val REGISTER_FILTER_SERVER = 301
    const val REGISTER_MESSAGE_FILTER_CLASS = 302
    const val QUERY_CONSUME_TIME_SPAN = 303
    const val GET_SYSTEM_TOPIC_LIST_FROM_NS = 304
    const val GET_SYSTEM_TOPIC_LIST_FROM_BROKER = 305
    const val CLEAN_EXPIRED_CONSUMEQUEUE = 306
    const val GET_CONSUMER_RUNNING_INFO = 307
    const val QUERY_CORRECTION_OFFSET = 308
    const val CONSUME_MESSAGE_DIRECTLY = 309
    const val SEND_MESSAGE_V2 = 310
    const val GET_UNIT_TOPIC_LIST = 311
    const val GET_HAS_UNIT_SUB_TOPIC_LIST = 312
    const val GET_HAS_UNIT_SUB_UNUNIT_TOPIC_LIST = 313
    const val CLONE_GROUP_OFFSET = 314
    const val VIEW_BROKER_STATS_DATA = 315
    const val CLEAN_UNUSED_TOPIC = 316
    const val GET_BROKER_CONSUME_STATS = 317

    /**
     * update the config of name server
     */
    const val UPDATE_NAMESRV_CONFIG = 318

    /**
     * get config from name server
     */
    const val GET_NAMESRV_CONFIG = 319
    const val SEND_BATCH_MESSAGE = 320
    const val QUERY_CONSUME_QUEUE = 321
    const val QUERY_DATA_VERSION = 322

    /**
     * resume logic of checking half messages that have been put in TRANS_CHECK_MAXTIME_TOPIC before
     */
    const val RESUME_CHECK_HALF_MESSAGE = 323
    const val SEND_REPLY_MESSAGE = 324
    const val SEND_REPLY_MESSAGE_V2 = 325
    const val PUSH_REPLY_MESSAGE_TO_CLIENT = 326
}