package com.agmtopy.kocketmq.remoting.protocol

enum class LanguageCode(val code: Byte) {
    JAVA(0.toByte()), CPP(1.toByte()), DOTNET(2.toByte()), PYTHON(3.toByte()), DELPHI(4.toByte()), ERLANG(5.toByte()), RUBY(
        6.toByte()
    ),
    OTHER(7.toByte()), HTTP(8.toByte()), GO(9.toByte()), PHP(10.toByte()), OMS(11.toByte());

    companion object {
        fun valueOf(code: Byte): LanguageCode? {
            for (languageCode in values()) {
                if (languageCode.code == code) {
                    return languageCode
                }
            }
            return null
        }
    }
}