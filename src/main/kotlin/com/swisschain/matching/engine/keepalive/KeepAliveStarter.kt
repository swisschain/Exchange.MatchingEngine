package com.swisschain.matching.engine.keepalive

import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.utils.AppVersion
import com.swisschain.utils.keepalive.http.IsAliveResponseGetter
import com.swisschain.utils.keepalive.http.KeepAliveStarter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
@Profile("default")
class KeepAliveStarter @Autowired constructor(private val meIsAliveResponseGetter: IsAliveResponseGetter,
                                              private val config: Config) {
    @PostConstruct
    private fun start() {
        KeepAliveStarter.start(config.me.keepAlive, meIsAliveResponseGetter, AppVersion.VERSION)
    }
}