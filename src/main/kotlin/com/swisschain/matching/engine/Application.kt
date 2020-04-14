package com.swisschain.matching.engine

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class Application {
    @Autowired
    lateinit var grpcServicesInit: Runnable

    fun run () {
        grpcServicesInit.run()
    }
}