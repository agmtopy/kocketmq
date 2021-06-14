package com.agmtopy.kocketmq.common.concurrent

import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.logging.InternalLogger
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


abstract class ServiceThread() : Runnable {
    private var thread: Thread? = null
    protected val waitPoint: CountDownLatch2 = CountDownLatch2(1)

    @Volatile
    protected var hasNotified = AtomicBoolean(false)

    @Volatile
    var isStopped = false
        protected set
    var isDaemon = false

    //Make it able to restart the thread
    private val started = AtomicBoolean(false)
    abstract val serviceName: String

    fun start() {
        log.info("Try to start service thread:$serviceName started:$started.get() lastThread:$thread")
        if (!started.compareAndSet(false, true)) {
            return
        }
        isStopped = false
        thread = Thread(this, serviceName)
        thread!!.isDaemon = isDaemon
        thread!!.start()
    }

    @JvmOverloads
    fun shutdown(interrupt: Boolean = false) {
        log.info("Try to shutdown service thread:$serviceName started:$started.get() lastThread:$thread")
        if (!started.compareAndSet(true, false)) {
            return
        }
        isStopped = true
        log.info("shutdown thread " + serviceName + " interrupt " + interrupt)
        if (hasNotified.compareAndSet(false, true)) {
            waitPoint.countDown() // notify
        }
        try {
            if (interrupt) {
                thread!!.interrupt()
            }
            val beginTime = System.currentTimeMillis()
            if (!thread!!.isDaemon) {
                thread!!.join(joinTime.toLong())
            }
            val elapsedTime = System.currentTimeMillis() - beginTime
            log.info(
                "join thread " + serviceName + " elapsed time(ms) " + elapsedTime + " "
                        + joinTime
            )
        } catch (e: InterruptedException) {
            log.error("Interrupted", e)
        }
    }

    @Deprecated("")
    fun stop() {
        this.stop(false)
    }

    @Deprecated("")
    fun stop(interrupt: Boolean) {
        if (!started.get()) {
            return
        }
        isStopped = true
        log.info("stop thread " + serviceName + " interrupt " + interrupt)
        if (hasNotified.compareAndSet(false, true)) {
            waitPoint.countDown() // notify
        }
        if (interrupt) {
            thread!!.interrupt()
        }
    }

    fun makeStop() {
        if (!started.get()) {
            return
        }
        isStopped = true
        log.info("makestop thread " + serviceName)
    }

    fun wakeup() {
        if (hasNotified.compareAndSet(false, true)) {
            waitPoint.countDown() // notify
        }
    }

    protected fun waitForRunning(interval: Long) {
        if (hasNotified.compareAndSet(true, false)) {
            onWaitEnd()
            return
        }

        //entry to wait
        waitPoint.reset()
        try {
            waitPoint.await(interval, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            log.error("Interrupted", e)
        } finally {
            hasNotified.set(false)
            onWaitEnd()
        }
    }

    protected fun onWaitEnd() {}

    companion object {
        private val log: InternalLogger = InternalLoggerFactory.getLogger(LoggerName.COMMON_LOGGER_NAME)
        private val joinTime = 9000
    }
}
