package com.agmtopy.kocketmq.common.util

import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.InetAddressValidator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.experimental.and

class UtilAll {

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.COMMON_LOGGER_NAME)
        const val YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss"
        const val YYYY_MM_DD_HH_MM_SS_SSS = "yyyy-MM-dd#HH:mm:ss:SSS"
        const val YYYYMMDDHHMMSS = "yyyyMMddHHmmss"
        val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

        // format: "pid@hostname"
        val pid: Int
            get() {
                val runtime = ManagementFactory.getRuntimeMXBean()
                val name = runtime.name // format: "pid@hostname"
                return try {
                    name.substring(0, name.indexOf('@')).toInt()
                } catch (e: Exception) {
                    -1
                }
            }

        fun sleep(sleepMs: Long) {
            if (sleepMs < 0) {
                return
            }
            try {
                Thread.sleep(sleepMs)
            } catch (ignored: Throwable) {
            }
        }

        fun currentStackTrace(): String {
            val sb = StringBuilder()
            val stackTrace = Thread.currentThread().stackTrace
            for (ste in stackTrace) {
                sb.append("\n\t")
                sb.append(ste.toString())
            }
            return sb.toString()
        }

        fun offset2FileName(offset: Long): String {
            val nf = NumberFormat.getInstance()
            nf.minimumIntegerDigits = 20
            nf.maximumFractionDigits = 0
            nf.isGroupingUsed = false
            return nf.format(offset)
        }

        fun computeElapsedTimeMilliseconds(beginTime: Long): Long {
            return System.currentTimeMillis() - beginTime
        }

        fun isItTimeToDo(`when`: String): Boolean {
            val whiles = `when`.split(";").toTypedArray()
            if (whiles.size > 0) {
                val now = Calendar.getInstance()
                for (w in whiles) {
                    val nowHour = w.toInt()
                    if (nowHour == now[Calendar.HOUR_OF_DAY]) {
                        return true
                    }
                }
            }
            return false
        }

        @JvmOverloads
        fun timeMillisToHumanString(t: Long = System.currentTimeMillis()): String {
            val cal = Calendar.getInstance()
            cal.timeInMillis = t
            return String.format(
                "%04d%02d%02d%02d%02d%02d%03d", cal[Calendar.YEAR], cal[Calendar.MONTH] + 1,
                cal[Calendar.DAY_OF_MONTH], cal[Calendar.HOUR_OF_DAY], cal[Calendar.MINUTE], cal[Calendar.SECOND],
                cal[Calendar.MILLISECOND]
            )
        }

        fun computeNextMorningTimeMillis(): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal[Calendar.HOUR_OF_DAY] = 0
            cal[Calendar.MINUTE] = 0
            cal[Calendar.SECOND] = 0
            cal[Calendar.MILLISECOND] = 0
            return cal.timeInMillis
        }

        fun computeNextMinutesTimeMillis(): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_MONTH, 0)
            cal.add(Calendar.HOUR_OF_DAY, 0)
            cal.add(Calendar.MINUTE, 1)
            cal[Calendar.SECOND] = 0
            cal[Calendar.MILLISECOND] = 0
            return cal.timeInMillis
        }

        fun computeNextHourTimeMillis(): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_MONTH, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal[Calendar.MINUTE] = 0
            cal[Calendar.SECOND] = 0
            cal[Calendar.MILLISECOND] = 0
            return cal.timeInMillis
        }

        fun computeNextHalfHourTimeMillis(): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_MONTH, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal[Calendar.MINUTE] = 30
            cal[Calendar.SECOND] = 0
            cal[Calendar.MILLISECOND] = 0
            return cal.timeInMillis
        }

        fun timeMillisToHumanString2(t: Long): String {
            val cal = Calendar.getInstance()
            cal.timeInMillis = t
            return String.format(
                "%04d-%02d-%02d %02d:%02d:%02d,%03d",
                cal[Calendar.YEAR],
                cal[Calendar.MONTH] + 1,
                cal[Calendar.DAY_OF_MONTH],
                cal[Calendar.HOUR_OF_DAY],
                cal[Calendar.MINUTE],
                cal[Calendar.SECOND],
                cal[Calendar.MILLISECOND]
            )
        }

        fun timeMillisToHumanString3(t: Long): String {
            val cal = Calendar.getInstance()
            cal.timeInMillis = t
            return String.format(
                "%04d%02d%02d%02d%02d%02d",
                cal[Calendar.YEAR],
                cal[Calendar.MONTH] + 1,
                cal[Calendar.DAY_OF_MONTH],
                cal[Calendar.HOUR_OF_DAY],
                cal[Calendar.MINUTE],
                cal[Calendar.SECOND]
            )
        }

        fun getDiskPartitionSpaceUsedPercent(path: String?): Double {
            if (null == path || path.isEmpty()) {
                log.error("Error when measuring disk space usage, path is null or empty, path : {}", path)
                return (-1).toDouble()
            }
            try {
                val file = File(path)
                if (!file.exists()) {
                    log.error("Error when measuring disk space usage, file doesn't exist on this path: {}", path)
                    return (-1).toDouble()
                }
                val totalSpace = file.totalSpace
                if (totalSpace > 0) {
                    val freeSpace = file.freeSpace
                    val usedSpace = totalSpace - freeSpace
                    return usedSpace / totalSpace.toDouble()
                }
            } catch (e: Exception) {
                log.error("Error when measuring disk space usage, got exception: :", e)
                return (-1).toDouble()
            }
            return (-1).toDouble()
        }

        fun crc32(array: ByteArray?): Int {
            return if (array != null) {
                crc32(array, 0, array.size)
            } else 0
        }

        fun crc32(array: ByteArray?, offset: Int, length: Int): Int {
            val crc32 = CRC32()
            crc32.update(array, offset, length)
            return (crc32.value and 0x7FFFFFFF).toInt()
        }

        fun bytes2string(src: ByteArray): String {
            val hexChars = CharArray(src.size * 2)
            for (j in src.indices) {
                val v: Int = src[j].toInt() and 0xFF
                hexChars[j * 2] = HEX_ARRAY[v ushr 4]
                hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
            }
            return String(hexChars)
        }

        fun string2bytes(hexString: String?): ByteArray? {
            var hexString = hexString
            if (hexString == null || hexString == "") {
                return null
            }
            hexString = hexString.toUpperCase()
            val length = hexString.length / 2
            val hexChars = hexString.toCharArray()
            val d = ByteArray(length)
            for (i in 0 until length) {
                val pos = i * 2
                //TODO 使用char位移,意义不明
                d[i] = (charToByte(hexChars[pos]).toInt() shl 4 or charToByte(hexChars[pos + 1]).toInt()) as Byte
            }
            return d
        }

        private fun charToByte(c: Char): Byte {
            return "0123456789ABCDEF".indexOf(c).toByte()
        }

        @Throws(IOException::class)
        fun uncompress(src: ByteArray): ByteArray {
            var result = src
            val uncompressData = ByteArray(src.size)
            val byteArrayInputStream = ByteArrayInputStream(src)
            val inflaterInputStream = InflaterInputStream(byteArrayInputStream)
            val byteArrayOutputStream = ByteArrayOutputStream(src.size)
            result = try {
                while (true) {
                    val len = inflaterInputStream.read(uncompressData, 0, uncompressData.size)
                    if (len <= 0) {
                        break
                    }
                    byteArrayOutputStream.write(uncompressData, 0, len)
                }
                byteArrayOutputStream.flush()
                byteArrayOutputStream.toByteArray()
            } catch (e: IOException) {
                throw e
            } finally {
                try {
                    byteArrayInputStream.close()
                } catch (e: IOException) {
                    log.error("Failed to close the stream", e)
                }
                try {
                    inflaterInputStream.close()
                } catch (e: IOException) {
                    log.error("Failed to close the stream", e)
                }
                try {
                    byteArrayOutputStream.close()
                } catch (e: IOException) {
                    log.error("Failed to close the stream", e)
                }
            }
            return result
        }

        @Throws(IOException::class)
        fun compress(src: ByteArray, level: Int): ByteArray {
            var result = src
            val byteArrayOutputStream = ByteArrayOutputStream(src.size)
            val defeater = Deflater(level)
            val deflaterOutputStream = DeflaterOutputStream(byteArrayOutputStream, defeater)
            result = try {
                deflaterOutputStream.write(src)
                deflaterOutputStream.finish()
                deflaterOutputStream.close()
                byteArrayOutputStream.toByteArray()
            } catch (e: IOException) {
                defeater.end()
                throw e
            } finally {
                try {
                    byteArrayOutputStream.close()
                } catch (ignored: IOException) {
                }
                defeater.end()
            }
            return result
        }

        fun asInt(str: String, defaultValue: Int): Int {
            return try {
                str.toInt()
            } catch (e: Exception) {
                defaultValue
            }
        }

        fun asLong(str: String, defaultValue: Long): Long {
            return try {
                str.toLong()
            } catch (e: Exception) {
                defaultValue
            }
        }

        fun formatDate(date: Date?, pattern: String?): String {
            val df = SimpleDateFormat(pattern)
            return df.format(date)
        }

        fun parseDate(date: String?, pattern: String?): Date? {
            val df = SimpleDateFormat(pattern)
            return try {
                df.parse(date)
            } catch (e: ParseException) {
                null
            }
        }

        fun responseCode2String(code: Int): String {
            return Integer.toString(code)
        }

        fun frontStringAtLeast(str: String?, size: Int): String? {
            if (str != null) {
                if (str.length > size) {
                    return str.substring(0, size)
                }
            }
            return str
        }

        fun isBlank(str: String?): Boolean {
            var strLen: Int = str!!.length
            if (str == null || str.length.also { strLen = it } == 0) {
                return true
            }
            for (i in 0 until strLen) {
                if (!Character.isWhitespace(str[i])) {
                    return false
                }
            }
            return true
        }

        @JvmOverloads
        fun jstack(map: Map<Thread, Array<StackTraceElement>?> = Thread.getAllStackTraces()): String {
            val result = StringBuilder()
            try {
                val ite = map.entries.iterator()
                while (ite.hasNext()) {
                    val (thread, elements) = ite.next()
                    if (elements != null && elements.size > 0) {
                        val threadName = thread.name
                        result.append(String.format("%-40sTID: %d STATE: %s%n", threadName, thread.id, thread.state))
                        for (el in elements) {
                            result.append(String.format("%-40s%s%n", threadName, el.toString()))
                        }
                        result.append("\n")
                    }
                }
            } catch (e: Throwable) {
                result.append(RemotingHelper.exceptionSimpleDesc(e))
            }
            return result.toString()
        }

        fun isInternalIP(ip: ByteArray): Boolean {
            if (ip.size != 4) {
                throw RuntimeException("illegal ipv4 bytes")
            }

            //10.0.0.0~10.255.255.255
            //172.16.0.0~172.31.255.255
            //192.168.0.0~192.168.255.255
            if (ip[0] == 10.toByte()) {
                return true
            } else if (ip[0] == 172.toByte()) {
                if (ip[1] >= 16.toByte() && ip[1] <= 31.toByte()) {
                    return true
                }
            } else if (ip[0] == 192.toByte()) {
                if (ip[1] == 168.toByte()) {
                    return true
                }
            }
            return false
        }

        fun isInternalV6IP(inetAddr: InetAddress): Boolean {
            return if (inetAddr.isAnyLocalAddress // Wild card ipv6
                || inetAddr.isLinkLocalAddress // Single broadcast ipv6 address: fe80:xx:xx...
                || inetAddr.isLoopbackAddress //Loopback ipv6 address
                || inetAddr.isSiteLocalAddress
            ) { // Site local ipv6 address: fec0:xx:xx...
                true
            } else false
        }

        private fun ipCheck(ip: ByteArray): Boolean {
            if (ip.size != 4) {
                throw RuntimeException("illegal ipv4 bytes")
            }
            val validator: InetAddressValidator = InetAddressValidator.getInstance()
            return validator.isValidInet4Address(ipToIPv4Str(ip))
        }

        private fun ipV6Check(ip: ByteArray): Boolean {
            if (ip.size != 16) {
                throw RuntimeException("illegal ipv6 bytes")
            }
            val validator: InetAddressValidator = InetAddressValidator.getInstance()
            return validator.isValidInet6Address(ipToIPv6Str(ip))
        }

        fun ipToIPv4Str(ip: ByteArray): String? {
            return if (ip.size != 4) {
                null
            } else StringBuilder().append(ip[0] and 0xFF.toByte()).append(".").append(
                ip[1] and 0xFF.toByte()
            ).append(".").append(ip[2] and 0xFF.toByte())
                .append(".").append(ip[3] and 0xFF.toByte()).toString()
        }

        fun ipToIPv6Str(ip: ByteArray): String? {
            if (ip.size != 16) {
                return null
            }
            val sb = StringBuilder()
            for (i in ip.indices) {
                val hex = Integer.toHexString((ip[i] and 0xFF.toByte()).toInt())
                if (hex.length < 2) {
                    sb.append(0)
                }
                sb.append(hex)
                if (i % 2 == 1 && i < ip.size - 1) {
                    sb.append(":")
                }
            }
            return sb.toString()
        }

        fun getIp(): ByteArray {
            try {
                val allNetInterfaces: Enumeration<*> = NetworkInterface.getNetworkInterfaces()
                var ip: InetAddress? = null
                var internalIP: ByteArray? = null
                while (allNetInterfaces.hasMoreElements()) {
                    val netInterface = allNetInterfaces.nextElement() as NetworkInterface
                    val addresses: Enumeration<*> = netInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        ip = addresses.nextElement() as InetAddress
                        if (ip != null && ip is Inet4Address) {
                            val ipByte = ip.getAddress()
                            if (ipByte.size == 4) {
                                if (ipCheck(ipByte)) {
                                    if (!isInternalIP(ipByte)) {
                                        return ipByte
                                    } else if (internalIP == null) {
                                        internalIP = ipByte
                                    }
                                }
                            }
                        } else if (ip != null && ip is Inet6Address) {
                            val ipByte = ip.getAddress()
                            if (ipByte.size == 16) {
                                if (ipV6Check(ipByte)) {
                                    if (!isInternalV6IP(ip)) {
                                        return ipByte
                                    }
                                }
                            }
                        }
                    }
                }
                return internalIP ?: throw RuntimeException("Can not get local ip")
            } catch (e: Exception) {
                throw RuntimeException("Can not get local ip", e)
            }
        }
    }


    fun deleteFile(file: File) {
        if (!file.exists()) {
            return
        }
        if (file.isFile) {
            file.delete()
        } else if (file.isDirectory) {
            val files = file.listFiles()
            for (file1 in files) {
                deleteFile(file1)
            }
            file.delete()
        }
    }

    fun list2String(list: List<String?>?, splitor: String?): String? {
        if (list == null || list.size == 0) {
            return null
        }
        val str = StringBuffer()
        for (i in list.indices) {
            str.append(list[i])
            if (i == list.size - 1) {
                continue
            }
            str.append(splitor)
        }
        return str.toString()
    }

    fun string2List(str: String, splitor: String?): List<String>? {
        if (StringUtils.isEmpty(str)) {
            return null
        }
        val addrArray = str.split(splitor!!).toTypedArray()
        return Arrays.asList(*addrArray)
    }
}