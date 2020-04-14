package com.swisschain.matching.engine.outgoing.publishers.events

class PublisherFailureEvent<E>(val publisherName: String, val failedEvent: E?)