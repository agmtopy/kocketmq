package com.agmtopy.kocketmq.logging

/**
 * 内部log接口
 */
interface InternalLogger {

    fun getName(): String

    fun info(s: String)

    fun info(var1: String?, var2: Any?)

    fun info(var1: String?, var2: Any?, var3: Any?)

    fun warn(s: String)

    fun warn(s: String, e: Throwable)

    fun warn(var1: String?, var2: Any?)

    fun warn(var1: String?, var2: Any?, var3: Any?)

    fun debug(s: String)

    fun debug(var1: String?, var2: Any?)

    fun debug(var1: String?, var2: Any?, var3: Any?)

    fun error(s: String)

    fun error(var1: String?, var2: Any?)

    fun error(var1: String?, var2: Any?, var3: Any?)
}