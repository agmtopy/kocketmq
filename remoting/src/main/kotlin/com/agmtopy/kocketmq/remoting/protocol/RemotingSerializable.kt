package com.agmtopy.kocketmq.remoting.protocol

import com.alibaba.fastjson.JSON
import java.nio.charset.Charset

/**
 * 远程序列化
 */
open abstract class RemotingSerializable {
    fun encode(): ByteArray? {
        val json = this.toJson()
        return json?.toByteArray(CHARSET_UTF8)
    }

    @JvmOverloads
    fun toJson(prettyFormat: Boolean = false): String? {
        return Companion.toJson(this, prettyFormat)
    }

    companion object {
        private val CHARSET_UTF8 = Charset.forName("UTF-8")
        fun encode(obj: Any?): ByteArray? {
            val json = Companion.toJson(obj, false)
            return json?.toByteArray(CHARSET_UTF8)
        }

        fun toJson(obj: Any?, prettyFormat: Boolean): String {
            return JSON.toJSONString(obj, prettyFormat)
        }

        fun <T> decode(data: ByteArray?, classOfT: Class<T>?): T {
            val json = String(data!!, CHARSET_UTF8)
            return fromJson(json, classOfT)
        }

        fun <T> fromJson(json: String?, classOfT: Class<T>?): T {
            return JSON.parseObject(json, classOfT)
        }
    }
}
