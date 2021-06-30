package com.agmtopy.kocketmq.common.concurrent

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong

class ThreadFactoryImpl:ThreadFactory {
    private var threadIndex = AtomicLong(0)
    private var threadNamePrefix: String? = null
    private var daemon = false

    constructor(threadNamePrefix: String?) {
        ThreadFactoryImpl(threadNamePrefix, false)
    }

    constructor(threadNamePrefix: String?, daemon: Boolean){
        this.threadNamePrefix = threadNamePrefix
        this.daemon = daemon
    }

    override fun newThread(r: Runnable?): Thread? {
        val thread = Thread(r, threadNamePrefix + threadIndex.incrementAndGet())
        thread.isDaemon = daemon
        return thread
    }
}