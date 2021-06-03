package com.agmtopy.kocketmq.remoting.protocol

enum class SerializeType(val code: Byte) {
    JSON(0.toByte()), ROCKETMQ(1.toByte());

    companion object {
        fun valueOf(code: Byte): SerializeType? {
            for (serializeType in values()) {
                if (serializeType.code == code) {
                    return serializeType
                }
            }
            return null
        }
    }
}