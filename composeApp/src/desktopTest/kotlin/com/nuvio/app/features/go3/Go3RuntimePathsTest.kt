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
            assertEquals(executable.canonicalFile, Go3RuntimePaths.executable(null, support, helper, unrelated))
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
    @Test fun gradleRunFindsCheckoutRuntimeWithStagedHelperAndIsolatedHome() {
        val workspace = Files.createTempDirectory("go3-review-paths-").toFile()
        try {
            val checkout = workspace.resolve("NuvioDesktop")
            val source = checkout.resolve("scripts/go3-live-probe").apply { mkdirs() }
            source.resolve("main.cjs").writeText("// source")
            val compose = checkout.resolve("composeApp").apply { mkdirs() }
            val resources = compose.resolve("build/app-resources/macos")
            val staged = resources.resolve("go3").apply { mkdirs() }
            staged.resolve("main.cjs").writeText("// staged")
            val runtime = workspace.resolve("go3-feasibility/runtime/Electron.app/Contents/MacOS/Electron")
            runtime.parentFile.mkdirs()
            runtime.writeText("fixture")
            runtime.setExecutable(true)

            val helper = Go3RuntimePaths.helper(null, resources.path, compose)
            assertEquals(staged.canonicalFile, helper)
            val support = workspace.resolve("isolated-home/Library/Application Support/Nuvio")
            assertEquals(runtime.canonicalFile, Go3RuntimePaths.executable(null, support, helper, compose))
        } finally { workspace.deleteRecursively() }
    }
}
