package com.agmtopy.kocketmq.common

import kotlin.jvm.JvmStatic

object TopicSysFlag {
    private const val FLAG_UNIT = 0x1 shl 0
    private const val FLAG_UNIT_SUB = 0x1 shl 1
    fun buildSysFlag(unit: Boolean, hasUnitSub: Boolean): Int {
        var sysFlag = 0
        if (unit) {
            sysFlag = sysFlag or FLAG_UNIT
        }
        if (hasUnitSub) {
            sysFlag = sysFlag or FLAG_UNIT_SUB
        }
        return sysFlag
    }

    fun setUnitFlag(sysFlag: Int): Int {
        return sysFlag or FLAG_UNIT
    }

    fun clearUnitFlag(sysFlag: Int): Int {
        return sysFlag and FLAG_UNIT.inv()
    }

    fun hasUnitFlag(sysFlag: Int): Boolean {
        return sysFlag and FLAG_UNIT == FLAG_UNIT
    }

    fun setUnitSubFlag(sysFlag: Int): Int {
        return sysFlag or FLAG_UNIT_SUB
    }

    fun clearUnitSubFlag(sysFlag: Int): Int {
        return sysFlag and FLAG_UNIT_SUB.inv()
    }

    fun hasUnitSubFlag(sysFlag: Int): Boolean {
        return sysFlag and FLAG_UNIT_SUB == FLAG_UNIT_SUB
    }

    @JvmStatic
    fun main(args: Array<String>) {
    }
}