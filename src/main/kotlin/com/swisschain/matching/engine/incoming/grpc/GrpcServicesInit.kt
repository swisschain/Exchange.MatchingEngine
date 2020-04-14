package com.swisschain.matching.engine.incoming.grpc

import com.swisschain.matching.engine.AppInitialData
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.incoming.MessageRouter
import com.swisschain.matching.engine.messages.MessageProcessor
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.utils.AppVersion
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ServerBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class GrpcServicesInit(private val messageProcessor: MessageProcessor,
                       private val appInitialData: AppInitialData,
                       private val genericLimitOrderService: GenericLimitOrderService,
                       private val assetsHolder: AssetsHolder,
                       private val assetsPairsHolder: AssetsPairsHolder,
                       private val balancesHolder: BalancesHolder): Runnable {

    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var messageRouter: MessageRouter

    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcServicesInit::class.java.name)
    }

    override fun run() {
        messageProcessor.start()

        MetricsLogger.getLogger().logWarning("Spot.${config.me.name} ${AppVersion.VERSION} : " +
                "Started : ${appInitialData.ordersCount} orders, ${appInitialData.stopOrdersCount} " +
                "stop orders,${appInitialData.balancesCount} " +
                "balances for ${appInitialData.clientsCount} clients")

        LOGGER.info("Starting gRPC services")
        with (config.me.grpcEndpoints) {
            ServerBuilder.forPort(cashApiServicePort).addService(CashApiService(messageRouter)).build().start()
            LOGGER.info("Starting CashApiService at $cashApiServicePort port")
            ServerBuilder.forPort(tradingApiServicePort).addService(TradingApiService(messageRouter)).build().start()
            LOGGER.info("Starting TradingApiService at $tradingApiServicePort port")
            ServerBuilder.forPort(orderBooksServicePort).addService(OrderBooksService(genericLimitOrderService, assetsHolder, assetsPairsHolder)).build().start()
            LOGGER.info("Starting OrderBooksService at $orderBooksServicePort port")
            ServerBuilder.forPort(balancesServicePort).addService(BalancesService(balancesHolder, config.me.defaultBroker)).build().start()
            LOGGER.info("Starting BalancesService at $balancesServicePort port")
        }
    }
}