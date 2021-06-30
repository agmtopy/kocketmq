package com.agmtopy.kocketmq.common.util

import java.lang.StringBuilder
import com.agmtopy.kocketmq.common.util.FAQUrl

object FAQUrl {
    const val APPLY_TOPIC_URL = "http://rocketmq.apache.org/docs/faq/"
    const val NAME_SERVER_ADDR_NOT_EXIST_URL = "http://rocketmq.apache.org/docs/faq/"
    const val GROUP_NAME_DUPLICATE_URL = "http://rocketmq.apache.org/docs/faq/"
    const val CLIENT_PARAMETER_CHECK_URL = "http://rocketmq.apache.org/docs/faq/"
    const val SUBSCRIPTION_GROUP_NOT_EXIST = "http://rocketmq.apache.org/docs/faq/"
    const val CLIENT_SERVICE_NOT_OK = "http://rocketmq.apache.org/docs/faq/"

    // FAQ: No route info of this topic, TopicABC
    const val NO_TOPIC_ROUTE_INFO = "http://rocketmq.apache.org/docs/faq/"
    const val LOAD_JSON_EXCEPTION = "http://rocketmq.apache.org/docs/faq/"
    const val SAME_GROUP_DIFFERENT_TOPIC = "http://rocketmq.apache.org/docs/faq/"
    const val MQLIST_NOT_EXIST = "http://rocketmq.apache.org/docs/faq/"
    const val UNEXPECTED_EXCEPTION_URL = "http://rocketmq.apache.org/docs/faq/"
    const val SEND_MSG_FAILED = "http://rocketmq.apache.org/docs/faq/"
    const val UNKNOWN_HOST_EXCEPTION = "http://rocketmq.apache.org/docs/faq/"
    private const val TIP_STRING_BEGIN = "\nSee "
    private const val TIP_STRING_END = " for further details."
    fun suggestTodo(url: String?): String {
        val sb = StringBuilder()
        sb.append(TIP_STRING_BEGIN)
        sb.append(url)
        sb.append(TIP_STRING_END)
        return sb.toString()
    }

    fun attachDefaultURL(errorMessage: String?): String? {
        if (errorMessage != null) {
            val index = errorMessage.indexOf(TIP_STRING_BEGIN)
            if (-1 == index) {
                val sb = StringBuilder()
                sb.append(errorMessage)
                sb.append("\n")
                sb.append("For more information, please visit the url, ")
                sb.append(UNEXPECTED_EXCEPTION_URL)
                return sb.toString()
            }
        }
        return errorMessage
    }
}