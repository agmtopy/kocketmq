package com.agmtopy.kocketmq.common.constant

object PermName {
    const val PERM_PRIORITY = 0x1 shl 3
    const val PERM_READ = 0x1 shl 2
    const val PERM_WRITE = 0x1 shl 1
    const val PERM_INHERIT = 0x1 shl 0
    fun perm2String(perm: Int): String {
        val sb = StringBuffer("---")
        if (isReadable(perm)) {
            sb.replace(0, 1, "R")
        }
        if (isWriteable(perm)) {
            sb.replace(1, 2, "W")
        }
        if (isInherited(perm)) {
            sb.replace(2, 3, "X")
        }
        return sb.toString()
    }

    fun isReadable(perm: Int): Boolean {
        return perm and PERM_READ == PERM_READ
    }

    fun isWriteable(perm: Int): Boolean {
        return perm and PERM_WRITE == PERM_WRITE
    }

    fun isInherited(perm: Int): Boolean {
        return perm and PERM_INHERIT == PERM_INHERIT
    }
}