package org.martin.ktar

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TarGzExpanderTest {

    private lateinit var dir: Path

    @Before
    fun setup() {
        dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("targztest")
        FileSystem.SYSTEM.createDirectories(dir)
        println("Test dir: $dir")
    }

    @After
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    /**
     * Untar and un gzip the tar.gz file
     */
    @Test
    fun untarAndUnGzipCrosswireTarGzFile() {
        val destFolder = dir.resolve("untargzcrosswire")
        val tarGzFile = "src/test/resources/mods.d.tar.gz".toPath()

        TarGzExpander().expandTarGzFile(tarGzFile, destFolder)

        val extractedFiles = destFolder.resolve("mods.d").list()
        assertNotNull(extractedFiles)
        assertEquals(419, extractedFiles.size)
        assertTrue(extractedFiles.find { it.name == "bbe.conf" } != null)
    }
}