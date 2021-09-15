package com.agmtopy.kocketmq.logging

/**
 * BrokerStartup 启动类
 */
class BrokerStartup {
    companion object {
        fun main() {
            print("BrokerStartup start ...")
            BrokerService().start()
        }
    }

}

/**
 * 主函数入口
 */
fun main(args: Array<String>) {
    BrokerStartup.main()
}
