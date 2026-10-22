package com.nuvio.app.features.settings

import kotlinx.serialization.json.JsonObject

internal expect object ThemeSettingsStorage {
    fun loadSelectedTheme(): String?
    fun saveSelectedTheme(themeName: String)
    fun loadCustomThemeColors(): String?
    fun saveCustomThemeColors(colors: String)
    fun loadAmoledEnabled(): Boolean?
    fun saveAmoledEnabled(enabled: Boolean)
    fun loadLiquidGlassNativeTabBarEnabled(): Boolean?
    fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean)
    fun loadDesktopNavigationLayout(): String?
    fun saveDesktopNavigationLayout(layoutName: String)
    fun loadSelectedAppLanguage(): String?
    fun saveSelectedAppLanguage(languageCode: String)
    fun applySelectedAppLanguage(languageCode: String)
    fun loadNavBarStyle(): String?
    fun saveNavBarStyle(styleKey: String)
    fun loadNavBarGlowEnabled(): Boolean?

    fun loadDetailNavigationEnabled(): Boolean?

    fun loadDetailNavigationAutoHide(): Boolean?
    fun saveNavBarGlowEnabled(enabled: Boolean)

    fun saveDetailNavigationEnabled(enabled: Boolean)

    fun saveDetailNavigationAutoHide(enabled: Boolean)
    fun loadDetailNavigationHideDelaySeconds(): Int?
    fun saveDetailNavigationHideDelaySeconds(seconds: Int)
    fun exportToSyncPayload(): JsonObject
    fun replaceFromSyncPayload(payload: JsonObject)
}
