package com.agmtopy.kocketmq.remoting

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.annotation.CFNotNull
import com.agmtopy.kocketmq.remoting.common.RemotingHelper
import com.agmtopy.kocketmq.remoting.exception.impl.RemotingCommandException
import com.agmtopy.kocketmq.remoting.protocol.*
import com.alibaba.fastjson.annotation.JSONField
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.HashMap
import java.util.concurrent.atomic.AtomicInteger


/**
 * 远程命令 TODO 待详细分析
 */
open class RemotingCommand constructor() {
    companion object {
        const val SERIALIZE_TYPE_PROPERTY = "rocketmq.serialize.type"
        const val SERIALIZE_TYPE_ENV = "ROCKETMQ_SERIALIZE_TYPE"
        const val REMOTING_VERSION_KEY = "rocketmq.remoting.version"
        private val log: InternalLogger = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)

        // 0, REQUEST_COMMAND
        private const val RPC_TYPE = 0

        // 0, RPC
        private const val RPC_ONEWAY = 1
        private val CLASS_HASH_MAP: MutableMap<Class<out CommandCustomHeader?>, Array<Field>?> =
            HashMap<Class<out CommandCustomHeader?>, Array<Field>?>()
        private val CANONICAL_NAME_CACHE: MutableMap<Class<*>, String?> = HashMap()

        // 1, Oneway
        // 1, RESPONSE_COMMAND
        private val NULLABLE_FIELD_CACHE: MutableMap<Field, Boolean> = HashMap()
        private val STRING_CANONICAL_NAME = String::class.java.canonicalName
        private val DOUBLE_CANONICAL_NAME_1 = Double::class.java.canonicalName
        private val DOUBLE_CANONICAL_NAME_2 = Double::class.javaPrimitiveType!!.canonicalName
        private val INTEGER_CANONICAL_NAME_1 = Int::class.java.canonicalName
        private val INTEGER_CANONICAL_NAME_2 = Int::class.javaPrimitiveType!!.canonicalName
        private val LONG_CANONICAL_NAME_1 = Long::class.java.canonicalName
        private val LONG_CANONICAL_NAME_2 = Long::class.javaPrimitiveType!!.canonicalName
        private val BOOLEAN_CANONICAL_NAME_1 = Boolean::class.java.canonicalName
        private val BOOLEAN_CANONICAL_NAME_2 = Boolean::class.javaPrimitiveType!!.canonicalName

        @Volatile
        private var configVersion = -1
        private val requestId = AtomicInteger(0)
        private var serializeTypeConfigInThisServer: SerializeType = SerializeType.JSON
        fun createRequestCommand(code: Int, customHeader: CommandCustomHeader?): RemotingCommand {
            val cmd = RemotingCommand()
            cmd.code = code
            cmd.customHeader = customHeader
            setCmdVersion(cmd)
            return cmd
        }

        private fun setCmdVersion(cmd: RemotingCommand) {
            if (configVersion >= 0) {
                cmd.version = configVersion
            } else {
                val v = System.getProperty(REMOTING_VERSION_KEY)
                if (v != null) {
                    val value = v.toInt()
                    cmd.version = value
                    configVersion = value
                }
            }
        }

        fun createResponseCommand(classHeader: Class<out CommandCustomHeader?>?): RemotingCommand? {
            return createResponseCommand(RemotingSysResponseCode.SYSTEM_ERROR, "not set any response code", classHeader)
        }

        @JvmOverloads
        fun createResponseCommand(
            code: Int, remark: String?,
            classHeader: Class<out CommandCustomHeader?>? = null
        ): RemotingCommand? {
            val cmd = RemotingCommand()
            cmd.markResponseType()
            cmd.code = code
            cmd.remark = remark
            setCmdVersion(cmd)
            if (classHeader != null) {
                try {
                    val objectHeader: CommandCustomHeader? = classHeader.newInstance()
                    cmd.customHeader = objectHeader
                } catch (e: InstantiationException) {
                    return null
                } catch (e: IllegalAccessException) {
                    return null
                }
            }
            return cmd
        }

        fun decode(array: ByteArray?): RemotingCommand? {
            val byteBuffer = ByteBuffer.wrap(array)
            return decode(byteBuffer)
        }

        fun decode(byteBuffer: ByteBuffer): RemotingCommand? {
            val length = byteBuffer.limit()
            val oriHeaderLen = byteBuffer.int
            val headerLength = getHeaderLength(oriHeaderLen)
            val headerData = ByteArray(headerLength)
            byteBuffer[headerData]
            val cmd = getProtocolType(oriHeaderLen)?.let { headerDecode(headerData, it) }
            val bodyLength = length - 4 - headerLength
            var bodyData: ByteArray? = null
            if (bodyLength > 0) {
                bodyData = ByteArray(bodyLength)
                byteBuffer[bodyData]
            }
            cmd!!.body = bodyData
            return cmd
        }

        fun getHeaderLength(length: Int): Int {
            return length and 0xFFFFFF
        }

        private fun headerDecode(headerData: ByteArray, type: SerializeType): RemotingCommand? {
            when (type) {
                SerializeType.JSON -> {
                    val resultJson: RemotingCommand =
                        RemotingSerializable.decode(headerData, RemotingCommand::class.java)
                    resultJson.setSerializeTypeCurrentRPC(type)
                    return resultJson
                }
                SerializeType.ROCKETMQ -> {
                    val resultRMQ: RemotingCommand = RocketMQSerializable.rocketMQProtocolDecode(headerData)
                    resultRMQ.setSerializeTypeCurrentRPC(type)
                    return resultRMQ
                }
                else -> {
                }
            }
            return null
        }

        private fun getProtocolType(source: Int): SerializeType? {
            return SerializeType.valueOf((source shr 24 and 0xFF).toByte())
        }

        fun createNewRequestId(): Int {
            return requestId.getAndIncrement()
        }

        fun getSerializeTypeConfigInThisServer(): SerializeType {
            return serializeTypeConfigInThisServer
        }

        private fun isBlank(str: String?): Boolean {
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

        fun markProtocolType(source: Int, type: SerializeType): ByteArray {
            val result = ByteArray(4)
            result[0] = type.code
            result[1] = (source shr 16 and 0xFF).toByte()
            result[2] = (source shr 8 and 0xFF).toByte()
            result[3] = (source and 0xFF).toByte()
            return result
        }

        init {
            val protocol = System.getProperty(SERIALIZE_TYPE_PROPERTY, System.getenv(SERIALIZE_TYPE_ENV))
            if (!isBlank(protocol)) {
                try {
                    serializeTypeConfigInThisServer = SerializeType.valueOf(protocol)
                } catch (e: IllegalArgumentException) {
                    throw RuntimeException(
                        "parser specified protocol error. protocol=$protocol", e
                    )
                }
            }
        }
    }

    var code = 0
    private var language: LanguageCode = LanguageCode.JAVA
    var version = 0
    var opaque = requestId.getAndIncrement()
    var flag = 0
    var remark: String? = null
    var extFields: HashMap<String, String>? = null

    @Transient
    private var customHeader: CommandCustomHeader? = null
    private var serializeTypeCurrentRPC: SerializeType = serializeTypeConfigInThisServer

    @Transient
    private var body: ByteArray? = null
    fun markResponseType() {
        val bits = 1 shl RPC_TYPE
        flag = flag or bits
    }

    fun readCustomHeader(): CommandCustomHeader? {
        return customHeader
    }

    fun writeCustomHeader(customHeader: CommandCustomHeader?) {
        this.customHeader = customHeader
    }

    @Throws(RemotingCommandException::class)
    fun decodeCommandCustomHeader(classHeader: Class<out CommandCustomHeader?>): CommandCustomHeader? {
        val objectHeader: CommandCustomHeader?
        objectHeader = try {
            classHeader.newInstance()
        } catch (e: InstantiationException) {
            return null
        } catch (e: IllegalAccessException) {
            return null
        }
        if (extFields != null) {
            val fields = getClazzFields(classHeader)
            for (field in fields!!) {
                if (!Modifier.isStatic(field.modifiers)) {
                    val fieldName = field.name
                    if (!fieldName.startsWith("this")) {
                        try {
                            val value = extFields!![fieldName]
                            if (null == value) {
                                if (!isFieldNullable(field)) {
                                    throw RemotingCommandException("the custom field <$fieldName> is null")
                                }
                                continue
                            }
                            field.isAccessible = true
                            val type = getCanonicalName(field.type)
                            var valueParsed: Any?
                            valueParsed = if (type == STRING_CANONICAL_NAME) {
                                value
                            } else if (type == INTEGER_CANONICAL_NAME_1 || type == INTEGER_CANONICAL_NAME_2) {
                                value.toInt()
                            } else if (type == LONG_CANONICAL_NAME_1 || type == LONG_CANONICAL_NAME_2) {
                                value.toLong()
                            } else if (type == BOOLEAN_CANONICAL_NAME_1 || type == BOOLEAN_CANONICAL_NAME_2) {
                                java.lang.Boolean.parseBoolean(value)
                            } else if (type == DOUBLE_CANONICAL_NAME_1 || type == DOUBLE_CANONICAL_NAME_2) {
                                value.toDouble()
                            } else {
                                throw RemotingCommandException("the custom field <$fieldName> type is not supported")
                            }
                            field[objectHeader] = valueParsed
                        } catch (e: Throwable) {
                            log.error("Failed field [{}] decoding", fieldName, e)
                        }
                    }
                }
            }
            objectHeader!!.checkFields()
        }
        return objectHeader
    }

    private fun getClazzFields(classHeader: Class<out CommandCustomHeader?>): Array<Field>? {
        var field = CLASS_HASH_MAP[classHeader]
        if (field == null) {
            field = classHeader.declaredFields
            synchronized(CLASS_HASH_MAP) { CLASS_HASH_MAP.put(classHeader, field) }
        }
        return field
    }

    private fun isFieldNullable(field: Field): Boolean {
        if (!NULLABLE_FIELD_CACHE.containsKey(field)) {
            val annotation: Annotation = field.getAnnotation(CFNotNull::class.java)
            synchronized(NULLABLE_FIELD_CACHE) { NULLABLE_FIELD_CACHE.put(field, annotation == null) }
        }
        return NULLABLE_FIELD_CACHE[field]!!
    }

    private fun getCanonicalName(clazz: Class<*>): String? {
        var name = CANONICAL_NAME_CACHE[clazz]
        if (name == null) {
            name = clazz.canonicalName
            synchronized(CANONICAL_NAME_CACHE) { CANONICAL_NAME_CACHE.put(clazz, name) }
        }
        return name
    }

    fun encode(): ByteBuffer {
        // 1> header length size
        var length = 4

        // 2> header data length
        val headerData = headerEncode()
        length += headerData!!.size

        // 3> body data length
        if (body != null) {
            length += body!!.size
        }
        val result = ByteBuffer.allocate(4 + length)

        // length
        result.putInt(length)

        // header length
        result.put(markProtocolType(headerData.size, serializeTypeCurrentRPC))

        // header data
        result.put(headerData)

        // body data;
        if (body != null) {
            result.put(body)
        }
        result.flip()
        return result
    }

    private fun headerEncode(): ByteArray? {
        makeCustomHeaderToNet()
        //kotlin 这种写法很特别
        return if (SerializeType.ROCKETMQ === serializeTypeCurrentRPC) {
            RocketMQSerializable.rocketMQProtocolEncode(this)
        } else {
            RemotingSerializable.encode(this)
        }
    }

    fun makeCustomHeaderToNet() {
        if (customHeader != null) {
            val fields = getClazzFields(customHeader!!.javaClass)
            if (null == extFields) {
                extFields = HashMap()
            }
            for (field in fields!!) {
                if (!Modifier.isStatic(field.modifiers)) {
                    val name = field.name
                    if (!name.startsWith("this")) {
                        var value: Any? = null
                        try {
                            field.isAccessible = true
                            value = field[customHeader]
                        } catch (e: Exception) {
                            log.error("Failed to access field [{}]", name, e)
                        }
                        if (value != null) {
                            extFields!![name] = value.toString()
                        }
                    }
                }
            }
        }
    }

    @JvmOverloads
    fun encodeHeader(bodyLength: Int = if (body != null) body!!.size else 0): ByteBuffer {
        // 1> header length size
        var length = 4

        // 2> header data length
        val headerData: ByteArray? = headerEncode()
        length += headerData!!.size

        // 3> body data length
        length += bodyLength
        val result = ByteBuffer.allocate(4 + length - bodyLength)

        // length
        result.putInt(length)

        // header length
        result.put(markProtocolType(headerData.size, serializeTypeCurrentRPC))

        // header data
        result.put(headerData)
        result.flip()
        return result
    }

    fun markOnewayRPC() {
        val bits = 1 shl RPC_ONEWAY
        flag = flag or bits
    }

    @get:JSONField(serialize = false)
    val isOnewayRPC: Boolean
        get() {
            val bits = 1 shl RPC_ONEWAY
            return flag and bits == bits
        }

    @get:JSONField(serialize = false)
    val type: RemotingCommandType
        get() = if (isResponseType) {
            RemotingCommandType.RESPONSE_COMMAND
        } else RemotingCommandType.REQUEST_COMMAND

    @get:JSONField(serialize = false)
    val isResponseType: Boolean
        get() {
            val bits = 1 shl RPC_TYPE
            return flag and bits == bits
        }

    fun getLanguage(): LanguageCode {
        return language
    }

    fun setLanguage(language: LanguageCode) {
        this.language = language
    }

    fun getBody(): ByteArray? {
        return body
    }

    fun setBody(body: ByteArray?) {
        this.body = body
    }

    fun addExtField(key: String, value: String) {
        if (null == extFields) {
            extFields = HashMap()
        }
        extFields!![key] = value
    }

    override fun toString(): String {
        return ("RemotingCommand [code=" + code + ", language=" + language + ", version=" + version + ", opaque=" + opaque + ", flag(B)="
                + Integer.toBinaryString(flag) + ", remark=" + remark + ", extFields=" + extFields + ", serializeTypeCurrentRPC="
                + serializeTypeCurrentRPC + "]")
    }

    fun getSerializeTypeCurrentRPC(): SerializeType {
        return serializeTypeCurrentRPC
    }

    fun setSerializeTypeCurrentRPC(serializeTypeCurrentRPC: SerializeType) {
        this.serializeTypeCurrentRPC = serializeTypeCurrentRPC
    }
}