package com.agmtopy.kocketmq.remoting.common

import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import java.util.*

abstract class ServiceThread() : Runnable {
    protected val thread: Thread
    protected val log: InternalLogger = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING)
    val jointime:Long =  90_000L

    @Volatile
    protected var hasNotified = false

    @Volatile
    var isStopped = false
        protected set
    abstract val serviceName: String

    fun start() {
        thread.start()
    }

    @JvmOverloads
    fun shutdown(interrupt: Boolean = false) {
        isStopped = true
        log.info("shutdown thread " + serviceName + " interrupt " + interrupt)
        synchronized(this) {
            if (!hasNotified) {
                hasNotified = true
                //notify
            }
        }
        try {
            if (interrupt) {
                thread.interrupt()
            }
            val beginTime = System.currentTimeMillis()
            thread.join(jointime)
            val elapsedTime = System.currentTimeMillis() - beginTime
            log.info(
                "join thread " + serviceName + " elapsed time(ms) " + elapsedTime + " "
                        + jointime
            )
        } catch (e: InterruptedException) {
            log.error("Interrupted", e)
        }
    }

    init {
        thread = Thread(this, serviceName)
    }
}