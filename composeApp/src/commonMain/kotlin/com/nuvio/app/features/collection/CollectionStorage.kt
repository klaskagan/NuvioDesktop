package com.nuvio.app.features.collection

internal expect object CollectionStorage {
    fun seasonalSeedVersion(): Int
    fun saveSeasonalSeedVersion(version: Int)
    fun streamingSeedVersion(): Int
    fun saveStreamingSeedVersion(version: Int)
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
