package com.agmtopy.kocketmq.remoting.common

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 封装Semaphore
 */
class SemaphoreReleaseOnlyOnce(semaphore: Semaphore?) {
    private val released = AtomicBoolean(false)
    private val semaphore: Semaphore? = null

    /**
     * 释放信号量
     */
    fun release() {
        if (semaphore != null) {
            if (released.compareAndSet(false, true)) {
                semaphore.release()
            }
        }
    }

    /**
     * 获取信号量
     */
    fun getSemaphore(): Semaphore? {
        return semaphore
    }
}