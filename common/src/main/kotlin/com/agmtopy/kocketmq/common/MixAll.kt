package com.agmtopy.kocketmq.common

import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.common.util.FAQUrl
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import java.io.*
import java.lang.Boolean
import java.lang.management.ManagementFactory
import java.lang.reflect.Modifier
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*
import kotlin.Any
import kotlin.Exception
import kotlin.Long
import kotlin.RuntimeException
import kotlin.String
import kotlin.Throwable
import kotlin.Throws

class MixAll {

    companion object {
        //常量
        val ROCKETMQ_HOME_ENV: String? = "ROCKETMQ_HOME"
        const val ROCKETMQ_HOME_PROPERTY = "rocketmq.home.dir"
        const val NAMESRV_ADDR_ENV = "NAMESRV_ADDR"
        const val NAMESRV_ADDR_PROPERTY = "rocketmq.namesrv.addr"
        const val MESSAGE_COMPRESS_LEVEL = "rocketmq.message.compressLevel"
        const val DEFAULT_NAMESRV_ADDR_LOOKUP = "jmenv.tbsite.net"
        val WS_DOMAIN_NAME = System.getProperty("rocketmq.namesrv.domain", DEFAULT_NAMESRV_ADDR_LOOKUP)
        val WS_DOMAIN_SUBGROUP = System.getProperty("rocketmq.namesrv.domain.subgroup", "nsaddr")
        const val DEFAULT_PRODUCER_GROUP = "DEFAULT_PRODUCER"
        const val DEFAULT_CONSUMER_GROUP = "DEFAULT_CONSUMER"
        const val TOOLS_CONSUMER_GROUP = "TOOLS_CONSUMER"
        const val FILTERSRV_CONSUMER_GROUP = "FILTERSRV_CONSUMER"
        const val MONITOR_CONSUMER_GROUP = "__MONITOR_CONSUMER"
        const val CLIENT_INNER_PRODUCER_GROUP = "CLIENT_INNER_PRODUCER"
        const val SELF_TEST_PRODUCER_GROUP = "SELF_TEST_P_GROUP"
        const val SELF_TEST_CONSUMER_GROUP = "SELF_TEST_C_GROUP"
        const val ONS_HTTP_PROXY_GROUP = "CID_ONS-HTTP-PROXY"
        const val CID_ONSAPI_PERMISSION_GROUP = "CID_ONSAPI_PERMISSION"
        const val CID_ONSAPI_OWNER_GROUP = "CID_ONSAPI_OWNER"
        const val CID_ONSAPI_PULL_GROUP = "CID_ONSAPI_PULL"
        const val CID_RMQ_SYS_PREFIX = "CID_RMQ_SYS_"
        val LOCAL_INET_ADDRESS: List<String> = MixAll.getLocalInetAddress()!!
        val LOCALHOST: String? = MixAll.localhost()
        const val DEFAULT_CHARSET = "UTF-8"
        const val MASTER_ID = 0L
        val CURRENT_JVM_PID: Long = MixAll.getPID()
        const val RETRY_GROUP_TOPIC_PREFIX = "%RETRY%"
        const val DLQ_GROUP_TOPIC_PREFIX = "%DLQ%"
        const val REPLY_TOPIC_POSTFIX = "REPLY_TOPIC"
        const val UNIQUE_MSG_QUERY_FLAG = "_UNIQUE_KEY_QUERY"
        const val DEFAULT_TRACE_REGION_ID = "DefaultRegion"
        const val CONSUME_CONTEXT_TYPE = "ConsumeContextType"
        const val CID_SYS_RMQ_TRANS = "CID_RMQ_SYS_TRANS"
        const val ACL_CONF_TOOLS_FILE = "/conf/tools.yml"
        const val REPLY_MESSAGE_FLAG = "reply"
        private val log: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.COMMON_LOGGER_NAME)

        /**
         * fileName -> String
         */
        fun file2String(fileName: String): String? {
            var file = File(fileName)
            return file2String(file)
        }

        /**
         * file -> String
         * 无文件时直接返回null(TODO需要处理)
         */
        private fun file2String(file: File): String? {
            if (!file.exists()) {
                return null
            }

            //返回file的内容
            file.inputStream().buffered().reader().use { reader -> return reader.readText() }
        }

        fun getLocalInetAddress(): List<String>? {
            val inetAddressList: MutableList<String> = ArrayList()
            try {
                val enumeration = NetworkInterface.getNetworkInterfaces()
                while (enumeration.hasMoreElements()) {
                    val networkInterface = enumeration.nextElement()
                    val addrs = networkInterface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        inetAddressList.add(addrs.nextElement().hostAddress)
                    }
                }
            } catch (e: SocketException) {
                throw RuntimeException("get local inet address fail", e)
            }
            return inetAddressList
        }

        //Reverse logic comparing to RemotingUtil method, consider refactor in RocketMQ 5.0
        @Throws(SocketException::class)
        fun getLocalhostByNetworkInterface(): String? {
            val candidatesHost: MutableList<String> = ArrayList()
            val enumeration = NetworkInterface.getNetworkInterfaces()
            while (enumeration.hasMoreElements()) {
                val networkInterface = enumeration.nextElement()
                // Workaround for docker0 bridge
                if ("docker0" == networkInterface.name || !networkInterface.isUp) {
                    continue
                }
                val addrs = networkInterface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val address = addrs.nextElement()
                    if (address.isLoopbackAddress) {
                        continue
                    }
                    //ip4 higher priority
                    if (address is Inet6Address) {
                        candidatesHost.add(address.getHostAddress())
                        continue
                    }
                    return address.hostAddress
                }
            }
            return if (!candidatesHost.isEmpty()) {
                candidatesHost[0]
            } else null
        }

        private fun localhost(): String? {
            return try {
                InetAddress.getLocalHost().hostAddress
            } catch (e: Throwable) {
                try {
                    val candidatesHost = getLocalhostByNetworkInterface()
                    if (candidatesHost != null) return candidatesHost
                } catch (ignored: java.lang.Exception) {
                }
                throw RuntimeException(
                    "InetAddress java.net.InetAddress.getLocalHost() throws UnknownHostException" + FAQUrl.suggestTodo(
                        FAQUrl.UNKNOWN_HOST_EXCEPTION
                    ), e
                )
            }
        }

        fun getPID(): Long {
            val processName = ManagementFactory.getRuntimeMXBean().name
            return if (processName != null && processName.length > 0) {
                try {
                    processName.split("@").toTypedArray()[0].toLong()
                } catch (e: java.lang.Exception) {
                    0
                }
            } else 0
        }

        fun string2Properties(str: String): Properties? {
            val properties = Properties()
            try {
                val `in`: InputStream = ByteArrayInputStream(str.toByteArray(charset(DEFAULT_CHARSET)))
                properties.load(`in`)
            } catch (e: java.lang.Exception) {
                log.error("Failed to handle properties", e)
                return null
            }
            return properties
        }

        @Throws(IOException::class)
        fun string2File(str: String?, fileName: String) {
            val tmpFile = "$fileName.tmp"
            MixAll.string2FileNotSafe(str, tmpFile)
            val bakFile = "$fileName.bak"
            val prevContent = file2String(fileName)
            if (prevContent != null) {
                MixAll.string2FileNotSafe(prevContent, bakFile)
            }
            var file = File(fileName)
            file.delete()
            file = File(tmpFile)
            file.renameTo(File(fileName))
        }

        @Throws(IOException::class)
        fun string2FileNotSafe(str: String?, fileName: String?) {
            val file = File(fileName)
            val fileParent = file.parentFile
            fileParent?.mkdirs()
            var fileWriter: FileWriter? = null
            try {
                fileWriter = FileWriter(file)
                fileWriter.write(str)
            } catch (e: IOException) {
                throw e
            } finally {
                fileWriter?.close()
            }
        }

        fun object2Properties(`object`: Any): Properties? {
            val properties = Properties()
            val fields = `object`.javaClass.declaredFields
            for (field in fields) {
                if (!Modifier.isStatic(field.modifiers)) {
                    val name = field.name
                    if (!name.startsWith("this")) {
                        var value: Any? = null
                        try {
                            field.isAccessible = true
                            value = field[`object`]
                        } catch (e: IllegalAccessException) {
                            log.error("Failed to handle properties", e)
                        }
                        if (value != null) {
                            properties.setProperty(name, value.toString())
                        }
                    }
                }
            }
            return properties
        }

        fun properties2String(properties: Properties): String? {
            val sb = StringBuilder()
            for ((key, value) in properties) {
                if (value != null) {
                    sb.append(""" $key=$value """.trimIndent()
                    )
                }
            }
            return sb.toString()
        }

        fun properties2Object(p: Properties, `object`: Any) {
            val methods = `object`.javaClass.methods
            for (method in methods) {
                val mn = method.name
                if (mn.startsWith("set")) {
                    try {
                        val tmp = mn.substring(4)
                        val first = mn.substring(3, 4)
                        val key = first.toLowerCase() + tmp
                        val property = p.getProperty(key)
                        if (property != null) {
                            val pt = method.parameterTypes
                            if (pt != null && pt.size > 0) {
                                val cn = pt[0].simpleName
                                var arg: Any? = null
                                arg = if (cn == "int" || cn == "Integer") {
                                    property.toInt()
                                } else if (cn == "long" || cn == "Long") {
                                    property.toLong()
                                } else if (cn == "double" || cn == "Double") {
                                    property.toDouble()
                                } else if (cn == "boolean" || cn == "Boolean") {
                                    Boolean.parseBoolean(property)
                                } else if (cn == "float" || cn == "Float") {
                                    property.toFloat()
                                } else if (cn == "String") {
                                    property
                                } else {
                                    continue
                                }
                                method.invoke(`object`, arg)
                            }
                        }
                    } catch (ignored: Throwable) {
                    }
                }
            }
        }
    }

    //成员方法
    private fun localhost(): String? {
        return try {
            InetAddress.getLocalHost().hostAddress
        } catch (e: Throwable) {
            try {
                val candidatesHost: String? = MixAll.getLocalhostByNetworkInterface()
                if (candidatesHost != null) return candidatesHost
            } catch (ignored: Exception) {
            }
            throw RuntimeException(
                "InetAddress java.net.InetAddress.getLocalHost() throws UnknownHostException" + FAQUrl.suggestTodo(
                    FAQUrl.UNKNOWN_HOST_EXCEPTION
                ), e
            )
        }
    }
}