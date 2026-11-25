package com.nuvio.app.core.format

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode

internal actual fun regionDisplayName(code: String, localeTag: String): String? =
    NSLocale(localeIdentifier = localeTag).displayNameForKey(NSLocaleCountryCode, code)?.takeIf { it.isNotBlank() && it != code }
