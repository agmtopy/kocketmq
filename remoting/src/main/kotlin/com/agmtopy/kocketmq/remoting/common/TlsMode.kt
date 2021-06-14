package com.agmtopy.kocketmq.remoting.common

enum class TlsMode(name: String) {
    DISABLED("disabled"), PERMISSIVE("permissive"), ENFORCING("enforcing");

    companion object {
        fun parse(mode: String): TlsMode {
            for (tlsMode in values()) {
                if (tlsMode.name == mode) {
                    return tlsMode
                }
            }
            return PERMISSIVE
        }
    }
}