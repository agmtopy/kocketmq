package com.agmtopy.kocketmq.logging.processor

import com.agmtopy.kocketmq.logging.NamesrvController

/**
 * 集群模式特殊请求处理
 */
class ClusterTestRequestProcessor : DefaultRequestProcessor {

    constructor(namesrvController: NamesrvController,productEnvName:String?) : super(namesrvController) {

    }

}