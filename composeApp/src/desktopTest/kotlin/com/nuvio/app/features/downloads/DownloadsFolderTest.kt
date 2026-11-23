package com.nuvio.app.features.downloads

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

/** Choosing a download folder (desktop): new downloads use it, earlier ones keep theirs. */
class DownloadsFolderTest {
    private val original = DownloadsFolder.picker

    @AfterTest
    fun restore() {
        DownloadsFolder.picker = original
        DownloadsFolder.reset()
    }

    @Test
    fun chooseResetAndRejectUnwritable() = runBlocking {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val chosen = Files.createTempDirectory("nuvio-downloads").toFile()
        DownloadsFolder.picker = { chosen }
        assertEquals(DownloadsFolderChoice.Chosen, DownloadsFolder.choose())
        assertEquals(chosen.absolutePath, DownloadsFolder.customPath.value)
        assertEquals(chosen.absolutePath, DownloadsFolder.currentPath())

        // A file where a folder should be can't hold downloads; the previous choice stays.
        val notAFolder = File.createTempFile("nuvio", ".txt")
        DownloadsFolder.picker = { notAFolder }
        assertEquals(DownloadsFolderChoice.NotWritable, DownloadsFolder.choose())
        assertEquals(chosen.absolutePath, DownloadsFolder.customPath.value)

        DownloadsFolder.picker = { null }
        assertEquals(DownloadsFolderChoice.Cancelled, DownloadsFolder.choose())

        DownloadsFolder.reset()
        assertNull(DownloadsFolder.customPath.value)
        assertEquals(DownloadsPlatformDownloader.defaultDownloadsDir.absolutePath, DownloadsFolder.currentPath())
    }

    @Test
    fun finishedFilesResolveInTheFolderTheyStartedIn() {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val old = Files.createTempDirectory("nuvio-old").toFile()
        File(old, "movie.mkv").writeText("x")
        // The item has no stored URI: it's found by name in its own folder, not the current one.
        val found = DownloadsPlatformDownloader.resolveLocalFileUri(null, "movie.mkv", old.absolutePath)
        assertEquals(File(old, "movie.mkv").toURI().toString(), found)
        assertNull(DownloadsPlatformDownloader.resolveLocalFileUri(null, "movie.mkv", null))
    }

    @Test
    fun shortPathsUseTilde() {
        assertEquals("~/Movies/Nuvio", shortFolderPath("/Users/me/Movies/Nuvio", "/Users/me"))
        assertEquals("/Volumes/Media", shortFolderPath("/Volumes/Media", "/Users/me"))
        assertEquals("/Users/meow", shortFolderPath("/Users/meow", "/Users/me"))
    }
}
