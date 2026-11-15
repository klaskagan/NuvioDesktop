package com.nuvio.app.core.format

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle

internal actual fun formatWholeUsd(amount: Long, localeTag: String): String =
    NSNumberFormatter().apply {
        locale = NSLocale(localeIdentifier = localeTag)
        numberStyle = NSNumberFormatterCurrencyStyle
        currencyCode = "USD"
        currencySymbol = "$"
        minimumFractionDigits = 0u
        maximumFractionDigits = 0u
    }.stringFromNumber(NSNumber(longLong = amount)) ?: "\$$amount"
