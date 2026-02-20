package org.martin.ktar

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import org.martin.ktar.TarHeader.Companion.createHeader
import org.martin.ktar.TarUtils.calculateTarSize
import org.martin.ktar.TestUtils.readFile
import org.martin.ktar.TestUtils.writeStringToFile

class TarTest {
    private lateinit var dir: Path

    @BeforeTest
    fun setup() {
        dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("tartest")
        FileSystem.SYSTEM.createDirectories(dir)
        println("Test dir: $dir")
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    /**
     * Tar the given folder
     */
    @Test
    fun tar() {
        val tarFile = dir.resolve("tartest.tar")
        val out = TarOutput(tarFile)
        println("Tar to: $tarFile")

        val tartest = dir.resolve("tartest")
        FileSystem.SYSTEM.createDirectories(tartest)
        println("Dir to tar: $tartest")

        writeStringToFile("HPeX2kD5kSTc7pzCDX", tartest.resolve("one"))
        writeStringToFile("gTzyuQjfhrnyX9cTBSy", tartest.resolve("two"))
        writeStringToFile("KG889vdgjPHQXUEXCqrr", tartest.resolve("three"))
        writeStringToFile("CNBDGjEJNYfms7rwxfkAJ", tartest.resolve("four"))
        writeStringToFile("tT6mFKuLRjPmUDjcVTnjBL", tartest.resolve("five"))
        writeStringToFile("jrPYpzLfWB5vZTRsSKqFvVj", tartest.resolve("six"))

        tarFolder(null, tartest, out)

        out.close()

        assertEquals(calculateTarSize(tartest), tarFile.length())
    }

    @Test
    fun tarFileWithUTF8Name() {
        // Russian file name, each symbol takes 2 bytes (except spaces), length is 109 bytes in UTF8,
        // must be truncated to max 100 bytes (see TarHeader.NAMELEN)
        // translation: "very very very very very very very long file name"
        val fullName = "очень очень очень очень очень очень очень длинное имя файла"
        val fullNameLength = 109
        assertEquals(fullName.encodeToByteArray().count(), fullNameLength)

        // last word should be cut off, new length should be 99 bytes
        val truncatedName = "очень очень очень очень очень очень очень длинное имя "
        val truncatedNameLength = 99
        assertEquals(truncatedName.encodeToByteArray().count(), truncatedNameLength)

        // create archive
        val tarFile = dir.resolve("utf8tartest.tar")
        val out = TarOutput(tarFile)
        println("Tar to: $tarFile")

        val tartest = dir.resolve("tartest")
        FileSystem.SYSTEM.createDirectories(tartest)
        println("Dir to tar: $tartest")

        val content = "sample content"
        writeStringToFile(content, tartest.resolve(fullName))

        tarFolder(null, tartest, out)

        out.close()

        assertEquals(calculateTarSize(tartest), tarFile.length())

        // untar archive
        val destFolder = dir.resolve("untartest")
        FileSystem.SYSTEM.createDirectories(destFolder)

        val zf = FileSystem.SYSTEM.source(tarFile)

        val tis = TarInput(zf.buffer())
        untar(tis, destFolder)

        tis.close()

        // check file with truncated name
        val filePathString = destFolder.resolve("tartest").toString() + tartest.toString()
        val filePath = dir.resolve(filePathString).resolve(truncatedName)
        val fileContent = readFile(filePath)
        assertEquals(content, fileContent)
    }

    /**
     * Untar the tar file
     */
    @Test
    fun untarTarFile() {
        val destFolder = dir.resolve("untartest")
        FileSystem.SYSTEM.createDirectories(destFolder)

        val zf = FileSystem.SYSTEM.source("src/androidUnitTest/resources/tartest.tar".toPath())

        val tis = TarInput(zf.buffer())
        untar(tis, destFolder)

        tis.close()

        assertFileContents(destFolder)
    }

    /**
     * Untar the tar file
     */
    @Test
    fun untarCrosswireTarFile() {
        val destFolder = dir.resolve("untarcrosswire")
        FileSystem.SYSTEM.createDirectories(destFolder)
        println("Untar crosswire tar to: $destFolder")

        val zf = FileSystem.SYSTEM.source("src/androidUnitTest/resources/mods.d.tar".toPath())

        val tis = TarInput(zf.buffer())
        untar(tis, destFolder)

        tis.close()

        val extractedFiles = destFolder.resolve("mods.d").list()
        assertNotNull(extractedFiles)
        assertEquals(419, extractedFiles.size)
        assertTrue(extractedFiles.find { it.name == "bbe.conf" } != null)
    }

    /**
     * Untar the tar file
     */
    @Test
    fun untarTarFileDefaultSkip() {
        val destFolder = dir.resolve("untartest/skip")
        FileSystem.SYSTEM.createDirectories(destFolder)

        val zf = FileSystem.SYSTEM.source("src/androidUnitTest/resources/tartest.tar".toPath())

        val tis = TarInput(zf.buffer())
        tis.isDefaultSkip = true
        untar(tis, destFolder)

        tis.close()

        assertFileContents(destFolder)
    }

    @Test
    fun testOffset() {
        val destFolder = dir.resolve("untartest")
        FileSystem.SYSTEM.createDirectories(destFolder)

        val zf = FileSystem.SYSTEM.source("src/androidUnitTest/resources/tartest.tar".toPath())

        val tis = TarInput(zf.buffer())
        tis.nextEntry
        assertEquals(TarConstants.HEADER_BLOCK.toLong(), tis.currentOffset)
        tis.nextEntry
        val entry = tis.nextEntry
        // All of the files in the tartest.tar file are smaller than DATA_BLOCK
        assertEquals((TarConstants.HEADER_BLOCK * 3 + TarConstants.DATA_BLOCK * 2).toLong(), tis.currentOffset)
        tis.close()
    }

    private fun untar(tis: TarInput, destFolder: Path) {
        var entry: TarEntry?
        while ((tis.nextEntry.also { entry = it }) != null) {
            println("Extracting: " + entry!!.name)
            var count: Int
            val data = ByteArray(BUFFER)

            if (entry!!.isDirectory) {
                FileSystem.SYSTEM.createDirectories(destFolder.resolve(entry!!.name))
                continue
            } else {
                val di = entry!!.name.lastIndexOf('/')
                if (di != -1) {
                    FileSystem.SYSTEM.createDirectories(destFolder.resolve(entry!!.name.substring(0, di)))
                }
            }

            println("Writing: " + destFolder + "/" + entry!!.name)
            val outPath = destFolder.resolve(entry!!.name)
            val dest = FileSystem.SYSTEM.sink(outPath).buffer()

            while ((tis.read(data).also { count = it }) != -1) {
                dest.write(data, 0, count)
            }

            dest.flush()
            dest.close()
        }
    }

    private fun tarFolder(parent: String?, path: Path, out: TarOutput) {
        println("tarFolder parent: $parent path: $path out: $out")
        val f = path
        var files = FileSystem.SYSTEM.list(f)

        // is file
        if (files.isEmpty()) {
            files = listOf(f)
        }

        val adjustedParent =
                if (parent == null)
                    if (f.isFile()) "" else f.name + "/"
                else parent + f.name + "/"

        for (i in files.indices) {
            println("Adding: " + files[i])
            var fe = f
            val data = ByteArray(BUFFER)

            if (f.isDirectory()) {
                fe = f.resolve(files[i])
            }

            if (fe.isDirectory()) {
                val fl = fe.list()
                if (fl.isNotEmpty()) {
                    tarFolder(adjustedParent, fe, out)
                } else {
                    val entry = TarEntry(fe, adjustedParent + files[i] + "/")
                    out.putNextEntry(entry)
                }
                continue
            }

            val origin = FileSystem.SYSTEM.source(fe).buffer()
            val entry = TarEntry(fe, adjustedParent + files[i])
            out.putNextEntry(entry)

            var count: Int

            while ((origin.read(data).also { count = it }) != -1) {
                out.write(data, 0, count)
            }

            out.flush()

            origin.close()
        }
    }

    @Test
    fun fileEntry() {
        val fileName = "file.txt"
        val fileSize: Long = 14523
        val modTime = Clock.System.now().toEpochMilliseconds() / 1000
        val permissions = 493

        // Create a header object and check the fields
        val fileHeader = createHeader(fileName, fileSize, modTime, false, permissions)
        assertEquals(fileName, fileHeader.name.toString())
        assertEquals(TarHeader.LF_NORMAL.toLong(), fileHeader.linkFlag.toLong())
        assertEquals(fileSize, fileHeader.size)
        assertEquals(modTime, fileHeader.modTime)
        assertEquals(permissions.toLong(), fileHeader.mode.toLong())

        // Create an entry from the header
        val fileEntry = TarEntry(fileHeader)
        assertEquals(fileName, fileEntry.name)

        // Write the header into a buffer, create it back and compare them
        val headerBuf = ByteArray(TarConstants.HEADER_BLOCK)
        fileEntry.writeEntryHeader(headerBuf)
        val createdEntry = TarEntry(headerBuf)
        assertTrue(fileEntry == createdEntry)
    }

    private fun assertFileContents(destFolder: Path) {
        assertEquals("HPeX2kD5kSTc7pzCDX", readFile(destFolder.resolve("tartest/one")))
        assertEquals("gTzyuQjfhrnyX9cTBSy", readFile(destFolder.resolve("tartest/two")))
        assertEquals("KG889vdgjPHQXUEXCqrr", readFile(destFolder.resolve("tartest/three")))
        assertEquals("CNBDGjEJNYfms7rwxfkAJ", readFile(destFolder.resolve("tartest/four")))
        assertEquals("tT6mFKuLRjPmUDjcVTnjBL", readFile(destFolder.resolve("tartest/five")))
        assertEquals("jrPYpzLfWB5vZTRsSKqFvVj", readFile(destFolder.resolve("tartest/six")))
    }

    companion object {
        const val BUFFER: Int = 2048
    }
}