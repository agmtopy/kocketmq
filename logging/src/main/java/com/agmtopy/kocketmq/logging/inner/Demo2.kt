package com.agmtopy.kocketmq.logging.inner

import kotlin.jvm.JvmStatic
import com.agmtopy.kocketmq.logging.inner.Demo1

object Demo2 {
    @JvmStatic
    fun main(args: Array<String>) {
        val demo1 = Demo1()
        demo1.method1()
    }
}