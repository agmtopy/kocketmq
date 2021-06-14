package com.agmtopy.kocketmq.remoting.netty

import com.agmtopy.kocketmq.remoting.InvokeCallback
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.common.SemaphoreReleaseOnlyOnce
import io.netty.channel.Channel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 响应的Future
 */
class ResponseFuture(
    processChannel: Channel?,
    opaque: Int,
    timeoutMillis: Long,
    invokeCallback: InvokeCallback?,
    once: SemaphoreReleaseOnlyOnce?
) {
    private var opaque = 0
    var processChannel: Channel? = null
    var timeoutMillis: Long = 0
    var invokeCallback: InvokeCallback? = null
    var beginTimestamp = System.currentTimeMillis()
    private var countDownLatch = CountDownLatch(1)

    //信号量
    private var once: SemaphoreReleaseOnlyOnce? = null

    private val executeCallbackOnlyOnce = AtomicBoolean(false)

    @Volatile
    public var responseCommand: RemotingCommand? = null

    @Volatile
    public var sendRequestOK = true

    @Volatile
    public var cause: Throwable? = null

    /**
     * 执行回调
     */
    fun executeInvokeCallback() {
        if (executeCallbackOnlyOnce.compareAndSet(false, true)) {
            invokeCallback!!.operationComplete(this)
        }
    }

    /**
     * 释放
     */
    fun release() {
        if (once != null) {
            once!!.release()
        }
    }

    /**
     * 判断是否超时
     */
    fun isTimeout(): Boolean {
        val diff = System.currentTimeMillis() - beginTimestamp
        return diff > timeoutMillis
    }

    /**
     * 等待响应
     */
    @Throws(InterruptedException::class)
    fun waitResponse(timeoutMillis: Long): RemotingCommand? {
        countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        return responseCommand
    }

    /**
     * set 响应
     */
    fun putResponse(responseCommand: RemotingCommand?) {
        this.responseCommand = responseCommand
        countDownLatch.countDown()
    }

    /**
     * 重新toString
     */
    override fun toString(): String {
        return ("ResponseFuture [responseCommand=" + responseCommand
                + ", sendRequestOK=" + sendRequestOK
                + ", cause=" + cause
                + ", opaque=" + opaque
                + ", processChannel=" + processChannel
                + ", timeoutMillis=" + timeoutMillis
                + ", invokeCallback=" + invokeCallback
                + ", beginTimestamp=" + beginTimestamp
                + ", countDownLatch=" + countDownLatch + "]")
    }
}