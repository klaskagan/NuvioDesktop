package com.nuvio.app.core.format

import java.util.Locale

internal actual fun regionDisplayName(code: String, localeTag: String): String? =
    Locale("", code).getDisplayCountry(Locale.forLanguageTag(localeTag)).takeIf { it.isNotBlank() && it != code }
