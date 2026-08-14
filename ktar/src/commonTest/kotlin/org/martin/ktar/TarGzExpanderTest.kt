package org.martin.ktar

import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TarGzExpanderTest {

    private lateinit var dir: Path

    @BeforeTest
    fun setup() {
        dir = TestUtils.createUniqueTestDir("targztest")
        println("Test dir: $dir")
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    /**
     * Untar and un gzip the tar.gz file
     */
    @Test
    fun untarAndUnGzipCrosswireTarGzFile() {
        val destFolder = dir.resolve("untargzcrosswire")
        val tarGzFile = TestConstants.CROSSWIRE_TAR_GZ_FILE

        TarGzExpander().expandTarGzFile(tarGzFile, destFolder)

        val extractedFiles = destFolder.resolve("mods.d").list()
        assertNotNull(extractedFiles)
        assertEquals(419, extractedFiles.size)
        assertTrue(extractedFiles.find { it.name == "bbe.conf" } != null)
    }

    /**
     * Get content of files in a tar.gz file
     */
    @Test
    fun handleContentOfTarGzFile() {
        val tarGzFile = TestConstants.CROSSWIRE_TAR_GZ_FILE

        var foundBSB = false
        TarGzExpander().handleTarGzContent(tarGzFile) { name, content ->
            if (name.endsWith("bsb.conf")) {
                val conf = content.readUtf8()
                foundBSB = true
                listOf("[BSB]", "DataPath=./modules/texts/ztext/bsb/", "ModDrv=zText", "BlockType=BOOK").forEach {
                    assertTrue( conf.contains(it))
                }
            }
        }
        assertTrue(foundBSB)
    }
}
