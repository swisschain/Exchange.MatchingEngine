package com.swisschain.matching.engine.services.events

import com.swisschain.matching.engine.daos.AssetPair

class RemovedAssetPairsEvent(val assetPairs: Collection<AssetPair>)