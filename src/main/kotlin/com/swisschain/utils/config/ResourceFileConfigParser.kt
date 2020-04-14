package com.swisschain.utils.config

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder

internal object ResourceFileConfigParser {
    fun <Config> initConfig(fileName: String, classOfT: Class<Config>): Config {
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create()
        return gson.fromJson(ResourceFileConfigParser::class.java.classLoader.getResource(fileName).readText(), classOfT)
    }
}
