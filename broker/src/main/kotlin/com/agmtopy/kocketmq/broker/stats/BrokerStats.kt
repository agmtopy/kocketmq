package com.agmtopy.kocketmq.broker.stats

import java.util.concurrent.atomic.AtomicLong

/**
 * Broker统计信息
 *
 * 统计Broker的各项指标：
 * - 消息发送数量
 * - 消息拉取数量
 * - 消息大小
 * - TPS统计
 */
class BrokerStats {

    // ==================== 消息统计 ====================

    /**
     * 发送消息总数
     */
    private val msgPutTotal = AtomicLong(0)

    /**
     * 发送消息成功数
     */
    private val msgPutSuccess = AtomicLong(0)

    /**
     * 发送消息失败数
     */
    private val msgPutFailed = AtomicLong(0)

    /**
     * 拉取消息总数
     */
    private val msgGetTotal = AtomicLong(0)

    /**
     * 拉取消息成功数
     */
    private val msgGetSuccess = AtomicLong(0)

    /**
     * 拉取消息失败数
     */
    private val msgGetFailed = AtomicLong(0)

    // ==================== 大小统计 ====================

    /**
     * 发送消息总大小（字节）
     */
    private val msgPutSizeTotal = AtomicLong(0)

    /**
     * 拉取消息总大小（字节）
     */
    private val msgGetSizeTotal = AtomicLong(0)

    // ==================== TPS统计 ====================

    /**
     * 上一秒发送TPS
     */
    private val putTps = AtomicLong(0)

    /**
     * 上一秒拉取TPS
     */
    private val getTps = AtomicLong(0)

    /**
     * TPS统计开始时间
     */
    private var tpsStartTime = System.currentTimeMillis()

    /**
     * TPS统计期间的发送数量
     */
    private val tpsPutCount = AtomicLong(0)

    /**
     * TPS统计期间的拉取数量
     */
    private val tpsGetCount = AtomicLong(0)

    // ==================== 记录方法 ====================

    /**
     * 记录消息发送
     */
    fun recordMsgPut(success: Boolean, size: Int) {
        msgPutTotal.incrementAndGet()
        if (success) {
            msgPutSuccess.incrementAndGet()
            msgPutSizeTotal.addAndGet(size.toLong())
            tpsPutCount.incrementAndGet()
        } else {
            msgPutFailed.incrementAndGet()
        }
    }

    /**
     * 记录消息拉取
     */
    fun recordMsgGet(success: Boolean, size: Int) {
        msgGetTotal.incrementAndGet()
        if (success) {
            msgGetSuccess.incrementAndGet()
            msgGetSizeTotal.addAndGet(size.toLong())
            tpsGetCount.incrementAndGet()
        } else {
            msgGetFailed.incrementAndGet()
        }
    }

    /**
     * 更新TPS
     */
    fun updateTps() {
        val now = System.currentTimeMillis()
        val elapsed = now - tpsStartTime

        if (elapsed >= 1000) {  // 每秒更新一次
            val putCount = tpsPutCount.getAndSet(0)
            val getCount = tpsGetCount.getAndSet(0)

            putTps.set(putCount * 1000 / elapsed)
            getTps.set(getCount * 1000 / elapsed)

            tpsStartTime = now
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 获取发送消息总数
     */
    fun getMsgPutTotal(): Long = msgPutTotal.get()

    /**
     * 获取发送成功数
     */
    fun getMsgPutSuccess(): Long = msgPutSuccess.get()

    /**
     * 获取发送失败数
     */
    fun getMsgPutFailed(): Long = msgPutFailed.get()

    /**
     * 获取拉取消息总数
     */
    fun getMsgGetTotal(): Long = msgGetTotal.get()

    /**
     * 获取拉取成功数
     */
    fun getMsgGetSuccess(): Long = msgGetSuccess.get()

    /**
     * 获取拉取失败数
     */
    fun getMsgGetFailed(): Long = msgGetFailed.get()

    /**
     * 获取发送消息总大小
     */
    fun getMsgPutSizeTotal(): Long = msgPutSizeTotal.get()

    /**
     * 获取拉取消息总大小
     */
    fun getMsgGetSizeTotal(): Long = msgGetSizeTotal.get()

    /**
     * 获取发送TPS
     */
    fun getPutTps(): Long = putTps.get()

    /**
     * 获取拉取TPS
     */
    fun getGetTps(): Long = getTps.get()

    /**
     * 获取统计信息
     */
    fun getStatsInfo(): Map<String, Any> {
        return mapOf(
            "msgPutTotal" to msgPutTotal.get(),
            "msgPutSuccess" to msgPutSuccess.get(),
            "msgPutFailed" to msgPutFailed.get(),
            "msgGetTotal" to msgGetTotal.get(),
            "msgGetSuccess" to msgGetSuccess.get(),
            "msgGetFailed" to msgGetFailed.get(),
            "msgPutSizeTotal" to msgPutSizeTotal.get(),
            "msgGetSizeTotal" to msgGetSizeTotal.get(),
            "putTps" to putTps.get(),
            "getTps" to getTps.get()
        )
    }

    /**
     * 重置统计
     */
    fun reset() {
        msgPutTotal.set(0)
        msgPutSuccess.set(0)
        msgPutFailed.set(0)
        msgGetTotal.set(0)
        msgGetSuccess.set(0)
        msgGetFailed.set(0)
        msgPutSizeTotal.set(0)
        msgGetSizeTotal.set(0)
        putTps.set(0)
        getTps.set(0)
        tpsPutCount.set(0)
        tpsGetCount.set(0)
        tpsStartTime = System.currentTimeMillis()
    }
}
