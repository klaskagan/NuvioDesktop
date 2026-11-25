package com.nuvio.app.core.format

/** The name of a country or region by its ISO 3166 code ("US" → "United States"), or null when unknown. */
internal expect fun regionDisplayName(code: String, localeTag: String): String?

/**
 * A metadata country field for display: two-letter codes ("US, GB") become names, and anything
 * else (already a name) is kept as it is.
 */
internal fun formatCountriesForDisplay(raw: String, localeTag: String): String =
    raw.split(',').map(String::trim).filter(String::isNotBlank).joinToString(", ") { token ->
        if (token.length == 2 && token.all(Char::isLetter)) regionDisplayName(token.uppercase(), localeTag) ?: token else token
    }
