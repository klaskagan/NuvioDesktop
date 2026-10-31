package com.nuvio.app.features.go3

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class Go3RuntimePathsTest {
    @Test fun packagedLaunchResolvesOutsideTheCheckout() {
        val root = Files.createTempDirectory("go3-paths-").toFile()
        try {
            val resources = root.resolve("Nuvio.app/Contents/app/resources").apply { mkdirs() }
            val helper = resources.resolve("go3").apply { mkdirs() }
            helper.resolve("main.cjs").writeText("// fixture")
            val unrelated = root.resolve("unrelated").apply { mkdirs() }
            val support = root.resolve("Application Support/Nuvio")
            val executable = support.resolve("Go3Runtime/Electron.app/Contents/MacOS/Electron")
            executable.parentFile.mkdirs()
            executable.writeText("fixture")
            executable.setExecutable(true)
            assertEquals(helper.canonicalFile, Go3RuntimePaths.helper(null, resources.path, unrelated))
            assertEquals(executable.canonicalFile, Go3RuntimePaths.executable(null, support, helper))
        } finally { root.deleteRecursively() }
    }

    @Test fun developmentLaunchStillResolvesFromComposeApp() {
        val root = Files.createTempDirectory("go3-dev-paths-").toFile()
        try {
            val helper = root.resolve("scripts/go3-live-probe").apply { mkdirs() }
            helper.resolve("main.cjs").writeText("// fixture")
            val compose = root.resolve("composeApp").apply { mkdirs() }
            assertEquals(helper.canonicalFile, Go3RuntimePaths.helper(null, null, compose))
        } finally { root.deleteRecursively() }
    }
}
