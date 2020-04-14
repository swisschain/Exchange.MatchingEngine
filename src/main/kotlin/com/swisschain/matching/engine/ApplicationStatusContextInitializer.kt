package com.swisschain.matching.engine

import com.swisschain.matching.engine.config.ConfigFactory
import com.swisschain.matching.engine.database.grpc.GrpcAliveStatusDatabaseAccessor
import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.utils.alivestatus.exception.CheckAppInstanceRunningException
import com.swisschain.utils.alivestatus.processor.AliveStatusProcessor
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.Environment
import java.net.InetAddress

class ApplicationStatusContextInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(applicationContext: GenericApplicationContext) {

        val statusProcessor = getStatusProcessor(getConfig(applicationContext.environment))

        try {
            statusProcessor.run()
        } catch (e: CheckAppInstanceRunningException) {
            LOGGER.error("Error occurred while starting application ${e.message}")
            System.exit(1)
        }
    }

    private fun getConfig(environment: Environment): Config {
        return ConfigFactory.getConfig(environment)
    }

    private fun getStatusProcessor(config: Config): Runnable {
        return AliveStatusProcessor(
                GrpcAliveStatusDatabaseAccessor(config.me.grpcEndpoints.aliveStatusConnection,
                        config.me.name, InetAddress.getLocalHost().hostAddress), config.me.aliveStatus)
    }
}