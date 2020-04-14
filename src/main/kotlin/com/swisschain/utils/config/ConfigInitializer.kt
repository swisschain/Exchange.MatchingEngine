package com.swisschain.utils.config

object ConfigInitializer {
    private val DEFAULT_LOCAL_FILE_NAME = "generalsettings.json"
    fun <Config> initConfig(url: String, localFileName: String = DEFAULT_LOCAL_FILE_NAME, classOfT: Class<Config>): Config {
        return if (url == "local") {
            ResourceFileConfigParser.initConfig(localFileName, classOfT)
        } else {
            HttpConfigParser.initConfig(url, classOfT)
        }

    }
}