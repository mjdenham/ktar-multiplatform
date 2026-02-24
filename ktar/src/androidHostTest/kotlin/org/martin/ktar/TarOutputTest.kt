package org.martin.ktar

import okio.FileSystem
import okio.Path
import okio.buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.martin.ktar.TestUtils.readFile
import org.martin.ktar.TestUtils.writeStringToFile

class TarOutputTest {
    private lateinit var dir: Path
    private lateinit var outDir: Path
    private lateinit var inDir: Path

    @BeforeTest
    fun setup() {
        dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("apnd")
        FileSystem.SYSTEM.createDirectories(dir)
        outDir = dir.resolve("out")
        FileSystem.SYSTEM.createDirectories(outDir)
        inDir = dir.resolve("in")
        FileSystem.SYSTEM.createDirectories(inDir)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(dir)
        FileSystem.SYSTEM.deleteRecursively(outDir)
        FileSystem.SYSTEM.deleteRecursively(inDir)
    }

    @Test
    fun testSingleOperation() {
        val path = dir.resolve("tar.tar")
        val tar = TarOutput(path)
        tar.putNextEntry(TarEntry(
            writeStringToFile("a", inDir.resolve("afile")
        ), "afile"))
        copyFileToStream(inDir.resolve("afile"), tar)
        tar.putNextEntry(TarEntry(writeStringToFile("b", inDir.resolve("bfile")), "bfile"))
        copyFileToStream(inDir.resolve("bfile"), tar)
        tar.putNextEntry(TarEntry(writeStringToFile("c", inDir.resolve("cfile")), "cfile"))
        copyFileToStream(inDir.resolve("cfile"), tar)
        tar.close()

        untar()

        assertInEqualsOut()
    }

    private fun copyFileToStream(file: Path, out: TarOutput) {
        val buffer = ByteArray(BUFFER)
        var length: Int

        FileSystem.SYSTEM.source(file).buffer().use { `in` ->
            while ((`in`.read(buffer).also { length = it }) > 0) {
                out.write(buffer, 0, length)
            }
        }
    }

    /**
     * Make sure that the contents of the input & output dirs are identical.
     */
    private fun assertInEqualsOut() {
        assertEquals(inDir.list().size, outDir.list()?.size)
        for (inFile in inDir.list()) {
            assertEquals(readFile(inFile), readFile(outDir.resolve(inFile.name)))
        }
    }

    private fun untar() {
        val zf = FileSystem.SYSTEM.source(dir.resolve("tar.tar")).buffer()

        TarInput(zf).use { tarInput ->
            var entry: TarEntry?
            while ((tarInput.nextEntry.also { entry = it }) != null) {
                var count: Int
                val data = ByteArray(2048)
                FileSystem.SYSTEM.sink(outDir.resolve(entry!!.name)).buffer().use { dest ->
                    while ((tarInput.read(data).also { count = it }) != -1) {
                        dest.write(data, 0, count)
                    }
                }
            }
        }
    }

    companion object {
        const val BUFFER: Int = 2048
    }
}