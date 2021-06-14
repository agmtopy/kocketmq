package com.agmtopy.kocketmq.logging.inner

import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.lang.InterruptedException

class Demo {
    private val readWriteLock: ReadWriteLock = ReentrantReadWriteLock()
    fun registerConfig(configObject: Any?): Demo {
        try {
            readWriteLock.writeLock().lockInterruptibly()
            try {
//                print("111")
//                val registerProps: Properties = MixAll.object2Properties(configObject)
//                merge(registerProps, this.allConfigs)
//                configObjectList.add(configObject)
            } finally {
                readWriteLock.writeLock().unlock()
            }
        } catch (e: InterruptedException) {
        }
        return this
    }
}