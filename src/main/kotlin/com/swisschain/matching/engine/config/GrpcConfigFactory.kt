package com.swisschain.matching.engine.config

import com.google.gson.FieldNamingPolicy.UPPER_CAMEL_CASE
import com.google.gson.GsonBuilder
import com.swisschain.matching.engine.config.grpc.ConfigServiceGrpc
import com.swisschain.matching.engine.config.grpc.GrpcConfigLoader
import com.swisschain.matching.engine.utils.config.Config
import io.grpc.ManagedChannelBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import javax.naming.ConfigurationException

class GrpcConfigFactory {

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger("AppStarter")

        fun getConfig(environment: Environment): Config {
            val commandLineArgs = environment.getProperty("nonOptionArgs", Array<String>::class.java)

            if (commandLineArgs == null || commandLineArgs.size < 2) {
                LOGGER.error("Not enough args. Usage: grpcConfigString instanceName")
                throw IllegalArgumentException("Not enough args. Usage: grpcConfigString instanceName")
            }

            return downloadConfig(commandLineArgs[0], commandLineArgs[1])
        }

        private fun downloadConfig(grpcConfig: String, appName: String): Config {
            try {
                val channel = ManagedChannelBuilder.forTarget(grpcConfig).usePlaintext().build()
                val grpcStub = ConfigServiceGrpc.newBlockingStub(channel)
                val grpcResponse = grpcStub.getConfig(GrpcConfigLoader.GrpcConfigRequest.newBuilder().setComponent(appName).build())
                channel.shutdown()

                val gson = GsonBuilder().setFieldNamingPolicy(UPPER_CAMEL_CASE).create()
                return gson.fromJson(grpcResponse.payload, Config::class.java)
            } catch (e: Exception) {
                throw ConfigurationException("Unable to read config from $grpcConfig: ${e.message}")
            }
        }
    }
}