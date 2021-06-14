package com.agmtopy.kocketmq.common

import java.io.File
import java.nio.file.Files

class MixAll {

    companion object {
        /**
         * fileName -> String
         */
        fun file2String(fileName: String): String? {
            var file = File(fileName)
            return file2String(file)
        }

        /**
         * file -> String
         * 无文件时直接返回null(TODO需要处理)
         */
        private fun file2String(file: File): String? {
            if (!file.exists()) {
                return null
            }

            //返回file的内容
            file.inputStream().buffered().reader().use { reader -> return reader.readText() }
        }
    }

    /**
     * 读取配置文件信息
     */
    fun object2Properties(configObject: Any) {

    }

}