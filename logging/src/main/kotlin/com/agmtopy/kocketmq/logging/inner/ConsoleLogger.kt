package com.agmtopy.kocketmq.logging.inner

import com.agmtopy.kocketmq.logging.InternalLogger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 控制台Logger实现
 * 将日志输出到控制台，用于测试和简单场景
 */
class ConsoleLogger(private val name: String) : InternalLogger {

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }

    override fun getName(): String = name

    override fun info(s: String) {
        println("[${LocalDateTime.now().format(formatter)}] INFO $name - $s")
    }

    override fun info(var1: String?, var2: Any?) {
        info("$var1".replaceFirst("{}", var2?.toString() ?: "null"))
    }

    override fun info(var1: String?, var2: Any?, var3: Any?) {
        info("$var1".replaceFirst("{}", var2?.toString() ?: "null").replaceFirst("{}", var3?.toString() ?: "null"))
    }

    override fun warn(s: String) {
        println("[${LocalDateTime.now().format(formatter)}] WARN $name - $s")
    }

    override fun warn(s: String, e: Throwable) {
        println("[${LocalDateTime.now().format(formatter)}] WARN $name - $s")
        e.printStackTrace()
    }

    override fun warn(var1: String?, var2: Any?) {
        warn("$var1".replaceFirst("{}", var2?.toString() ?: "null"))
    }

    override fun warn(var1: String?, var2: Any?, var3: Any?) {
        warn("$var1".replaceFirst("{}", var2?.toString() ?: "null").replaceFirst("{}", var3?.toString() ?: "null"))
    }

    override fun debug(s: String) {
        println("[${LocalDateTime.now().format(formatter)}] DEBUG $name - $s")
    }

    override fun debug(var1: String?, var2: Any?) {
        debug("$var1".replaceFirst("{}", var2?.toString() ?: "null"))
    }

    override fun debug(var1: String?, var2: Any?, var3: Any?) {
        debug("$var1".replaceFirst("{}", var2?.toString() ?: "null").replaceFirst("{}", var3?.toString() ?: "null"))
    }

    override fun error(s: String) {
        System.err.println("[${LocalDateTime.now().format(formatter)}] ERROR $name - $s")
    }

    override fun error(var1: String?, var2: Any?) {
        error("$var1".replaceFirst("{}", var2?.toString() ?: "null"))
    }

    override fun error(var1: String?, var2: Any?, var3: Any?) {
        error("$var1".replaceFirst("{}", var2?.toString() ?: "null").replaceFirst("{}", var3?.toString() ?: "null"))
    }
}
