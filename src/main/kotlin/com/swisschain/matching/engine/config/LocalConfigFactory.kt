package com.swisschain.matching.engine.config

import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.utils.config.ConfigInitializer

class LocalConfigFactory  {
    companion object {
        fun getConfig(): Config {
            return ConfigInitializer.initConfig("local",  classOfT = Config::class.java)
        }
    }
}