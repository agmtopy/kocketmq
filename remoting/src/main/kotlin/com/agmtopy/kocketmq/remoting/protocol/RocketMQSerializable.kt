package com.agmtopy.kocketmq.remoting.protocol

import com.agmtopy.kocketmq.remoting.RemotingCommand
import java.nio.ByteBuffer
import java.nio.charset.Charset

object RocketMQSerializable {
    private val CHARSET_UTF8 = Charset.forName("UTF-8")
    fun rocketMQProtocolEncode(cmd: RemotingCommand): ByteArray {
        // String remark
        var remarkBytes: ByteArray? = null
        var remarkLen = 0
        remarkBytes = cmd.remark!!.toByteArray(CHARSET_UTF8)
        remarkLen = remarkBytes.size

        // HashMap<String, String> extFields
        var extFieldsBytes: ByteArray? = null
        var extLen = 0
        extFieldsBytes = mapSerialize(cmd.extFields!!)
        extLen = extFieldsBytes!!.size
        val totalLen = calTotalLen(remarkLen, extLen)
        val headerBuffer = ByteBuffer.allocate(totalLen)
        // int code(~32767)
        headerBuffer.putShort(cmd.code as Short)
        // LanguageCode language
        headerBuffer.put(cmd.getLanguage().getCode())
        // int version(~32767)
        headerBuffer.putShort(cmd.version as Short)
        // int opaque
        headerBuffer.putInt(cmd.opaque)
        // int flag
        headerBuffer.putInt(cmd.flag)
        // String remark
        if (remarkBytes != null) {
            headerBuffer.putInt(remarkBytes.size)
            headerBuffer.put(remarkBytes)
        } else {
            headerBuffer.putInt(0)
        }
        // HashMap<String, String> extFields;
        if (extFieldsBytes != null) {
            headerBuffer.putInt(extFieldsBytes.size)
            headerBuffer.put(extFieldsBytes)
        } else {
            headerBuffer.putInt(0)
        }
        return headerBuffer.array()
    }

    fun mapSerialize(map: java.util.HashMap<String, String>): ByteArray? {
        // keySize+key+valSize+val
        if (null == map || map.isEmpty()) return null
        var totalLength = 0
        var kvLength: Int
        var it: Iterator<Map.Entry<String?, String?>> = map.entries.iterator()
        while (it.hasNext()) {
            val (key, value) = it.next()
            if (key != null && value != null) {
                kvLength =  // keySize + Key
                    (2 + key.toByteArray(CHARSET_UTF8).size // valSize + val
                            + 4 + value.toByteArray(CHARSET_UTF8).size)
                totalLength += kvLength
            }
        }
        val content = ByteBuffer.allocate(totalLength)
        var key: ByteArray
        var `val`: ByteArray
        it = map.entries.iterator()
        while (it.hasNext()) {
            val (key1, value) = it.next()
            if (key1 != null && value != null) {
                key = key1.toByteArray(CHARSET_UTF8)
                `val` = value.toByteArray(CHARSET_UTF8)
                content.putShort(key.size.toShort())
                content.put(key)
                content.putInt(`val`.size)
                content.put(`val`)
            }
        }
        return content.array()
    }

    private fun calTotalLen(remark: Int, ext: Int): Int {
        // int code(~32767)
        return (2 // LanguageCode language
                + 1 // int version(~32767)
                + 2 // int opaque
                + 4 // int flag
                + 4 // String remark
                + 4 + remark // HashMap<String, String> extFields
                + 4 + ext)
    }

    fun rocketMQProtocolDecode(headerArray: ByteArray?): RemotingCommand {
        val cmd = RemotingCommand()
        val headerBuffer = ByteBuffer.wrap(headerArray)
        // int code(~32767)
        cmd.code = headerBuffer.short.toInt()
        // LanguageCode language
        cmd.setLanguage(LanguageCode.valueOf(headerBuffer.get()))
        // int version(~32767)
        cmd.version = headerBuffer.short.toInt()
        // int opaque
        cmd.opaque = headerBuffer.int
        // int flag
        cmd.flag = headerBuffer.int
        // String remark
        val remarkLength = headerBuffer.int
        if (remarkLength > 0) {
            val remarkContent = ByteArray(remarkLength)
            headerBuffer[remarkContent]
            cmd.remark = String(remarkContent, CHARSET_UTF8)
        }

        // HashMap<String, String> extFields
        val extFieldsLength = headerBuffer.int
        if (extFieldsLength > 0) {
            val extFieldsBytes = ByteArray(extFieldsLength)
            headerBuffer[extFieldsBytes]
            cmd.extFields = mapDeserialize(extFieldsBytes)
        }
        return cmd
    }

    fun mapDeserialize(bytes: ByteArray?): HashMap<String, String>? {
        if (bytes == null || bytes.size <= 0) return null
        val map = HashMap<String, String>()
        val byteBuffer = ByteBuffer.wrap(bytes)
        var keySize: Short
        var keyContent: ByteArray
        var valSize: Int
        var valContent: ByteArray
        while (byteBuffer.hasRemaining()) {
            keySize = byteBuffer.short
            keyContent = ByteArray(keySize.toInt())
            byteBuffer[keyContent]
            valSize = byteBuffer.int
            valContent = ByteArray(valSize)
            byteBuffer[valContent]
            map[String(keyContent, CHARSET_UTF8)] = String(valContent, CHARSET_UTF8)
        }
        return map
    }

    fun isBlank(str: String?): Boolean {
        var strLen: Int
        if (str == null || str.isEmpty()) {
            return true
        }
        strLen = str.length
        for (i in 0 until strLen) {
            if (!Character.isWhitespace(str[i])) {
                return false
            }
        }
        return true
    }
}
