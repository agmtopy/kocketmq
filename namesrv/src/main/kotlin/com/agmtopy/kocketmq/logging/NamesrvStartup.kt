package com.agmtopy.kocketmq.logging

import com.agmtopy.kocketmq.common.constant.LoggerName
import com.agmtopy.kocketmq.common.namesrv.NamesrvConfig
import com.agmtopy.kocketmq.logging.inner.InternalLoggerFactory
import com.agmtopy.kocketmq.remoting.RemotingCommand
import com.agmtopy.kocketmq.remoting.netty.NettyServerConfig
import java.lang.IllegalArgumentException

/**
 * NamesrvStartup 启动类
 */
class NamesrvStartup {

    /**
     * 主函数入口
     */
    fun main(args: Array<String>) {
        main0(args)
    }

    /**
     * 具体执行逻辑:避免在main函数中添加过多的逻辑
     */
    fun main0(args: Array<String>) {
        try {
            var controller = createNamesrvController(args)
            start(controller)
            val tip =
                "The Name Server boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer()
            log!!.info(tip);
            println(tip)
        } catch (e: Throwable) {

        }
    }

    /**
     * 静态对象
     */
    companion object {
        //获取日志记录对象
        var log = buildLog()

        //Namesrv属性对象
        var properties = null

        //CommandLine解析启动入参(apache-Commons CLI项目提供支持)
        var commandLine = null

        /**
         * 创建Namersrv对象
         */
        fun createNamesrvController(args: Array<String>): NamesrvController {
            return NamesrvController(NamesrvConfig(), NettyServerConfig())
        }

        /**
         * 启动NamesrvController
         */
        fun start(controller: NamesrvController){
            if (controller == null) {
                throw IllegalArgumentException("NamesrvController is null")
            }

            //NamesrvController执行初始化,如果失败时退出进程
            if(controller.initialize()){
                controller.shutdown()
            }
            controller.start()
        }

        /**
         * 通过InternalLoggerFactory获取log对象
         */
        private fun buildLog(): InternalLogger? {
            return InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);
        }
    }
}