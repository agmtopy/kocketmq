package com.agmtopy.kocketmq.common.namesrv

import java.io.File

/**
 * NameServer config
 */
class NamesrvConfig {
    var kvConfigPath = System.getProperty("user.home") + File.separator + "namesrv" + File.separator + "kvConfig.json"

}