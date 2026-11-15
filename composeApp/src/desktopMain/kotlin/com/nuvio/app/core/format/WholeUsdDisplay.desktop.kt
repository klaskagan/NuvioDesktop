package com.nuvio.app.core.format

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

internal actual fun formatWholeUsd(amount: Long, localeTag: String): String =
    (NumberFormat.getCurrencyInstance(Locale.forLanguageTag(localeTag)) as DecimalFormat).apply {
        decimalFormatSymbols = decimalFormatSymbols.apply { currencySymbol = "$" }
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }.format(amount)
