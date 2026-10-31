package com.nuvio.app.features.go3

import java.io.File

/** Packaged launches must not depend on the shell's current directory. */
internal object Go3RuntimePaths {
    fun helper(override: String?, resources: String?, workingDirectory: File): File {
        val candidates = override?.let { listOf(File(it)) } ?: buildList {
            resources?.takeIf { it.isNotBlank() }?.let { add(File(it, "go3")) }
            add(File(workingDirectory, "scripts/go3-live-probe"))
            add(File(workingDirectory, "../scripts/go3-live-probe"))
        }
        return candidates.firstOrNull { File(it, "main.cjs").isFile }?.canonicalFile
            ?: error("Go3 helper unavailable. Rebuild the desktop application.")
    }

    fun executable(override: String?, support: File, helper: File): File {
        val candidates = override?.let { listOf(File(it)) } ?: listOf(
            File(support, "Go3Runtime/Electron.app/Contents/MacOS/Electron"),
            File(helper.parentFile.parentFile.parentFile,
                "go3-feasibility/runtime/Electron.app/Contents/MacOS/Electron"),
        )
        return candidates.firstOrNull { it.isFile && it.canExecute() }?.canonicalFile
            ?: error("Go3 runtime unavailable. Install the local Go3 browser runtime.")
    }
}
