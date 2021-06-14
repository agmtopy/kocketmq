package com.agmtopy.kocketmq.remoting.exception

/**
 * 自定义Remoting Exception
 */
open class RemotingException : Exception {
    constructor(message: String?) : super(message) {}
    constructor(message: String?, cause: Throwable?) : super(message, cause) {}
    constructor()

    companion object {
        private const val serialVersionUID = -5690687334570505110L
    }
}
