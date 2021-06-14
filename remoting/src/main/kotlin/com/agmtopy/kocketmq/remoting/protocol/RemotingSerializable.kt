package com.agmtopy.kocketmq.remoting.protocol

import com.alibaba.fastjson.JSON
import java.nio.charset.Charset

/**
 * 远程序列化
 */
open class RemotingSerializable {

    companion object {
        private val CHARSET_UTF8 = Charset.forName("UTF-8")

        fun encode(obj: Any?): ByteArray? {
            val json = toJson(obj, false)
            return json?.toByteArray(this.CHARSET_UTF8)
        }

        fun toJson(obj: Any?, prettyFormat: Boolean): String? {
            return JSON.toJSONString(obj, prettyFormat)
        }

        fun <T> decode(data: ByteArray?, classOfT: Class<T>?): T {
            val json = String(data!!, this.CHARSET_UTF8)
            return fromJson(json, classOfT)
        }

        @JvmStatic
        fun <T> fromJson(json: String?, classOfT: Class<T>?): T {
            return JSON.parseObject(json, classOfT)
        }

        fun encode(): ByteArray? {
            val json = this.toJson()
            return json?.toByteArray(this.CHARSET_UTF8)
        }

        fun toJson(): String? {
            return toJson(false)
        }

        fun toJson(prettyFormat: Boolean): String? {
            return toJson(this, prettyFormat)
        }
    }

}