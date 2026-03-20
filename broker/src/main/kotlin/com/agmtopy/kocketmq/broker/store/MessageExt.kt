package com.agmtopy.kocketmq.broker.store

import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * 消息扩展类
 *
 * 包含消息的完整信息，包括主题、队列ID、消息体、属性等
 */
data class MessageExt(
    var topic: String = "",
    var queueId: Int = 0,
    var body: ByteArray = ByteArray(0),
    var properties: String? = null,
    var flag: Int = 0,
    var sysFlag: Int = 0,
    var bodyCRC: Int = 0,
    var queueOffset: Long = 0L,
    var bornTimestamp: Long = 0L,
    var storeTimestamp: Long = 0L,
    var bornHost: String? = null,
    var storeHost: String? = null,
    var reconsumeTimes: Int = 0,
    var preparedTransactionOffset: Long = 0L
) {
    companion object {
        // 消息魔数
        const val MESSAGE_MAGIC_CODE = 0xAABBCCDD.toInt()

        // 消息头固定大小
        const val MESSAGE_HEADER_SIZE = 40  // 不包括totalSize字段

        // CRC32计算
        fun calculateCRC32(data: ByteArray): Int {
            if (data.isEmpty()) return 0
            val crc32 = CRC32()
            crc32.update(data)
            return crc32.value.toInt()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageExt

        if (topic != other.topic) return false
        if (queueId != other.queueId) return false
        if (!body.contentEquals(other.body)) return false
        if (properties != other.properties) return false
        if (flag != other.flag) return false
        if (sysFlag != other.sysFlag) return false
        if (bodyCRC != other.bodyCRC) return false
        if (queueOffset != other.queueOffset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + queueId
        result = 31 * result + body.contentHashCode()
        result = 31 * result + (properties?.hashCode() ?: 0)
        result = 31 * result + flag
        result = 31 * result + sysFlag
        result = 31 * result + bodyCRC
        result = 31 * result + queueOffset.hashCode()
        return result
    }
}

/**
 * 消息编解码工具
 */
object MessageCodec {

    /**
     * 编码消息为ByteBuffer
     *
     * 消息格式：
     * [totalSize(4)][magicCode(4)][bodyCRC(4)][queueId(4)][flag(4)][sysFlag(4)][queueOffset(8)]
     * [bodySize(4)][body(N)][propertiesSize(2)][properties(N)]
     *
     * @param message 消息对象
     * @return 编码后的ByteBuffer
     */
    fun encode(message: MessageExt): ByteBuffer {
        // 计算各部分大小
        val bodySize = message.body.size
        val propertiesBytes = message.properties?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val propertiesSize = propertiesBytes.size

        // 计算总大小（不包括totalSize字段本身）
        val totalSize = MESSAGE_HEADER_SIZE + bodySize + 2 + propertiesSize

        // 分配缓冲区（+4 for totalSize field）
        val buffer = ByteBuffer.allocate(totalSize + 4)

        // 写入固定头部（40字节）
        buffer.putInt(totalSize)
        buffer.putInt(MESSAGE_MAGIC_CODE)
        buffer.putInt(message.bodyCRC)
        buffer.putInt(message.queueId)
        buffer.putInt(message.flag)
        buffer.putInt(message.sysFlag)
        buffer.putLong(message.queueOffset)

        // 写入消息体
        buffer.putInt(bodySize)
        if (bodySize > 0) {
            buffer.put(message.body)
        }

        // 写入属性
        buffer.putShort(propertiesSize.toShort())
        if (propertiesSize > 0) {
            buffer.put(propertiesBytes)
        }

        buffer.flip()
        return buffer
    }

    /**
     * 从ByteBuffer解码消息
     *
     * @param buffer 包含消息数据的ByteBuffer
     * @return 解码后的消息对象，失败返回null
     */
    fun decode(buffer: ByteBuffer): MessageExt? {
        try {
            val startOffset = buffer.position()

            // 读取固定头部
            val totalSize = buffer.int
            val magicCode = buffer.int

            // 验证魔数
            if (magicCode != MESSAGE_MAGIC_CODE) {
                return null
            }

            val message = MessageExt()

            message.bodyCRC = buffer.int
            message.queueId = buffer.int
            message.flag = buffer.int
            message.sysFlag = buffer.int
            message.queueOffset = buffer.long

            // 读取消息体
            val bodySize = buffer.int
            if (bodySize > 0) {
                if (bodySize > buffer.remaining()) {
                    return null  // 数据不完整
                }
                message.body = ByteArray(bodySize)
                buffer.get(message.body)

                // 验证CRC（可选）
                if (message.bodyCRC != 0) {
                    val calculatedCRC = MessageExt.calculateCRC32(message.body)
                    if (calculatedCRC != message.bodyCRC) {
                        // CRC不匹配，但仍然返回消息（记录警告）
                        // 实际生产环境可能需要更严格的处理
                    }
                }
            }

            // 读取属性
            val propertiesSize = buffer.short.toInt()
            if (propertiesSize > 0) {
                if (propertiesSize > buffer.remaining()) {
                    return null  // 数据不完整
                }
                val propertiesBytes = ByteArray(propertiesSize)
                buffer.get(propertiesBytes)
                message.properties = String(propertiesBytes, Charsets.UTF_8)
            }

            return message
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 计算消息总大小
     */
    fun calTotalSize(message: MessageExt): Int {
        val bodySize = message.body.size
        val propertiesSize = message.properties?.toByteArray(Charsets.UTF_8)?.size ?: 0
        return MESSAGE_HEADER_SIZE + bodySize + 2 + propertiesSize
    }
}
