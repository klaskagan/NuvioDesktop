package com.nuvio.app.features.go3

import java.io.File

/** Packaged launches must not depend on the shell's current directory. */
internal object Go3RuntimePaths {
    fun helper(override: String?, resources: String?, workingDirectory: File): File {
        val candidates = override?.let { listOf(File(it)) } ?: buildList {
            resources?.takeIf { it.isNotBlank() }?.let { add(File(it, "go3")) }
            addAll(checkoutHelpers(workingDirectory))
        }
        return candidates.firstOrNull { File(it, "main.cjs").isFile }?.canonicalFile
            ?: error("Go3 helper unavailable. Rebuild the desktop application.")
    }

    fun executable(override: String?, support: File, helper: File, workingDirectory: File): File {
        val candidates = override?.let { listOf(File(it)) } ?: buildList {
            add(File(support, "Go3Runtime/Electron.app/Contents/MacOS/Electron"))
            // Gradle run can use a staged helper, so its path alone does not identify the checkout.
            val sourceHelpers = checkoutHelpers(workingDirectory).filter { File(it, "main.cjs").isFile }
            for (source in listOf(helper) + sourceHelpers) {
                val workspace = source.canonicalFile.parentFile?.parentFile?.parentFile ?: continue
                add(File(workspace, "go3-feasibility/runtime/Electron.app/Contents/MacOS/Electron"))
            }
        }
        return candidates.firstOrNull { it.isFile && it.canExecute() }?.canonicalFile
            ?: error("Go3 runtime unavailable. Install the local Go3 browser runtime.")
    }

    private fun checkoutHelpers(workingDirectory: File): List<File> = listOf(
        File(workingDirectory, "scripts/go3-live-probe"),
        File(workingDirectory, "../scripts/go3-live-probe"),
    )
}
