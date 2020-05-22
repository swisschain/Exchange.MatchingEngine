package com.swisschain.matching.engine.daos.context

import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.messages.MessageType

class LimitOrderMassCancelOperationContext(val brokerId: String,
                                           val uid: String,
                                           val messageId: String,
                                           val walletId: Long?,
                                           val processedMessage: ProcessedMessage,
                                           val messageType: MessageType,
                                           val assetPairId: String?,
                                           val isBuy: Boolean?)