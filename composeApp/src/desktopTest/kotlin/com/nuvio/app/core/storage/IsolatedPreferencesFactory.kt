package com.nuvio.app.core.storage

import java.util.prefs.AbstractPreferences
import java.util.prefs.Preferences
import java.util.prefs.PreferencesFactory

/** JVM preferences (including Supabase auth) must not reach the macOS account preferences in UI tests. */
class IsolatedPreferencesFactory : PreferencesFactory {
    private val root = MemoryPreferences(null, "")
    override fun userRoot(): Preferences = root
    override fun systemRoot(): Preferences = root

    private class MemoryPreferences(parent: AbstractPreferences?, name: String) : AbstractPreferences(parent, name) {
        private val values = mutableMapOf<String, String>()
        private val children = mutableMapOf<String, MemoryPreferences>()
        override fun putSpi(key: String, value: String) { values[key] = value }
        override fun getSpi(key: String): String? = values[key]
        override fun removeSpi(key: String) { values.remove(key) }
        override fun removeNodeSpi() { values.clear(); children.clear() }
        override fun keysSpi(): Array<String> = values.keys.toTypedArray()
        override fun childrenNamesSpi(): Array<String> = children.keys.toTypedArray()
        override fun childSpi(name: String): AbstractPreferences = children.getOrPut(name) { MemoryPreferences(this, name) }
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }
}
