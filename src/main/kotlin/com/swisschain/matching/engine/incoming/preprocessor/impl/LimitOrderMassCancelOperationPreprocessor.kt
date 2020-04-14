package com.swisschain.matching.engine.incoming.preprocessor.impl

import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderMassCancelOperationParsedData
import com.swisschain.matching.engine.incoming.parsers.impl.LimitOrderMassCancelOperationContextParser
import com.swisschain.matching.engine.incoming.preprocessor.AbstractMessagePreprocessor
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.utils.logging.ThrottlingLogger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingQueue

@Component
class LimitOrderMassCancelOperationPreprocessor(limitOrderMassCancelOperationContextParser: LimitOrderMassCancelOperationContextParser,
                                                messageProcessingStatusHolder: MessageProcessingStatusHolder,
                                                preProcessedMessageQueue: BlockingQueue<MessageWrapper>,
                                                @Qualifier("limitOrderMassCancelPreProcessingLogger")
                                                private val logger: ThrottlingLogger) :
        AbstractMessagePreprocessor<LimitOrderMassCancelOperationParsedData>(limitOrderMassCancelOperationContextParser,
                messageProcessingStatusHolder,
                preProcessedMessageQueue,
                logger) {

    override fun preProcessParsedData(parsedData: LimitOrderMassCancelOperationParsedData): Boolean {
        return true
    }
}
