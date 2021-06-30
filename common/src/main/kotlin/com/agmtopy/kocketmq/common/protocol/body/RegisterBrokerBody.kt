package com.agmtopy.kocketmq.common.protocol.body

import com.agmtopy.kocketmq.common.DataVersion
import com.agmtopy.kocketmq.common.MixAll
import com.agmtopy.kocketmq.common.TopicConfig
import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.protocol.RemotingSerializable
import com.alibaba.fastjson.JSON
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

class RegisterBrokerBody : RemotingSerializable() {
    var topicConfigSerializeWrapper = TopicConfigSerializeWrapper()
    var filterServerList: List<String> = ArrayList()

    fun encode(compress: Boolean): ByteArray? {
        if (!compress) {
            return super.encode()
        }
        val start = System.currentTimeMillis()
        val byteArrayOutputStream = ByteArrayOutputStream()
        val outputStream = DeflaterOutputStream(byteArrayOutputStream, Deflater(Deflater.BEST_COMPRESSION))
        val dataVersion: DataVersion = topicConfigSerializeWrapper.dataVersion
        val topicConfigTable: ConcurrentMap<String, TopicConfig> =
            cloneTopicConfigTable(topicConfigSerializeWrapper.topicConfigTable)
        try {
            var buffer: ByteArray? = dataVersion.encode()

            // write data version
            outputStream.write(convertIntToByteArray(buffer!!.size))
            outputStream.write(buffer)
            val topicNumber = topicConfigTable.size

            // write number of topic configs
            outputStream.write(convertIntToByteArray(topicNumber))

            // write topic config entry one by one.
            for ((_, value) in topicConfigTable) {
                buffer = value.encode().toByteArray()
                outputStream.write(convertIntToByteArray(buffer.size))
                outputStream.write(buffer)
            }
            buffer = JSON.toJSONString(filterServerList).toByteArray()
            // write filter server list json length
            outputStream.write(convertIntToByteArray(buffer.size))

            // write filter server list json
            outputStream.write(buffer)
            outputStream.finish()
            val interval = System.currentTimeMillis() - start
            if (interval > 50) {
                LOGGER.info("Compressing takes {}ms", interval)
            }
            return byteArrayOutputStream.toByteArray()
        } catch (e: IOException) {
            LOGGER.error("Failed to compress RegisterBrokerBody object", e)
        }
        return null
    }

    companion object {
        private val LOGGER: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.COMMON_LOGGER_NAME)
        @Throws(IOException::class)
        fun decode(data: ByteArray?, compressed: Boolean): RegisterBrokerBody {
            if (!compressed) {
                return decode(data, RegisterBrokerBody::class.java)
            }
            val start = System.currentTimeMillis()
            val inflaterInputStream = InflaterInputStream(ByteArrayInputStream(data))
            val dataVersionLength = readInt(inflaterInputStream)
            val dataVersionBytes = readBytes(inflaterInputStream, dataVersionLength)
            val dataVersion: DataVersion = decode(dataVersionBytes, DataVersion::class.java)
            val registerBrokerBody = RegisterBrokerBody()
            registerBrokerBody.topicConfigSerializeWrapper.dataVersion = dataVersion
            val topicConfigTable: ConcurrentMap<String, TopicConfig> =
                registerBrokerBody.topicConfigSerializeWrapper.topicConfigTable
            val topicConfigNumber = readInt(inflaterInputStream)
            LOGGER.debug("{} topic configs to extract", topicConfigNumber)
            for (i in 0 until topicConfigNumber) {
                val topicConfigJsonLength = readInt(inflaterInputStream)
                val topicConfig = TopicConfig()
                topicConfig.decode(readBytes(inflaterInputStream, topicConfigJsonLength).toString())
                topicConfigTable[topicConfig.topicName] = topicConfig
            }
            val filterServerListJsonLength = readInt(inflaterInputStream)
            val filterServerListBuffer = readBytes(inflaterInputStream, filterServerListJsonLength)
            val filterServerListJson = filterServerListBuffer.toString()
            var filterServerList: List<String> = ArrayList()
            try {
                filterServerList = JSON.parseArray<String>(filterServerListJson, String::class.java)
            } catch (e: Exception) {
                LOGGER.error("Decompressing occur Exception {}", filterServerListJson)
            }
            registerBrokerBody.filterServerList = filterServerList
            val interval = System.currentTimeMillis() - start
            if (interval > 50) {
                LOGGER.info("Decompressing takes {}ms", interval)
            }
            return registerBrokerBody
        }

        private fun convertIntToByteArray(n: Int): ByteArray {
            val byteBuffer = ByteBuffer.allocate(4)
            byteBuffer.putInt(n)
            return byteBuffer.array()
        }

        @Throws(IOException::class)
        private fun readBytes(inflaterInputStream: InflaterInputStream, length: Int): ByteArray {
            val buffer = ByteArray(length)
            var bytesRead = 0
            while (bytesRead < length) {
                val len = inflaterInputStream.read(buffer, bytesRead, length - bytesRead)
                bytesRead += if (len == -1) {
                    throw IOException("End of compressed data has reached")
                } else {
                    len
                }
            }
            return buffer
        }

        @Throws(IOException::class)
        private fun readInt(inflaterInputStream: InflaterInputStream): Int {
            val buffer = readBytes(inflaterInputStream, 4)
            val byteBuffer = ByteBuffer.wrap(buffer)
            return byteBuffer.int
        }

        fun cloneTopicConfigTable(
            topicConfigConcurrentMap: ConcurrentMap<String, TopicConfig>?
        ): ConcurrentMap<String, TopicConfig> {
            val result: ConcurrentHashMap<String, TopicConfig> = ConcurrentHashMap<String, TopicConfig>()
            if (topicConfigConcurrentMap != null) {
                for ((key, value) in topicConfigConcurrentMap) {
                    result[key] = value
                }
            }
            return result
        }
    }
}