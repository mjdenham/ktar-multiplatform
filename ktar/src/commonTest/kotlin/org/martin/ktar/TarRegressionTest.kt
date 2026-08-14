package org.martin.ktar

import okio.Buffer
import okio.FileSystem
import okio.GzipSink
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.martin.ktar.TarHeader.Companion.createHeader

class TarRegressionTest {
    private lateinit var dir: Path

    @BeforeTest
    fun setup() {
        dir = TestUtils.createUniqueTestDir("regression")
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    @Test
    fun truncatedArchiveInPaddingThrowsRatherThanHanging() {
        val full = singleEntryTar(createHeader("afile", 10, 0, false, 493), "0123456789")
        val truncated = full.copyOfRange(0, TarConstants.HEADER_BLOCK + 10)

        val tarInput = TarInput(Buffer().apply { write(truncated) })
        val entry = tarInput.nextEntry
        assertNotNull(entry)
        assertEquals("afile", entry.name)

        val data = ByteArray(64)
        while (tarInput.read(data) != -1) { /* drain the entry's content */ }

        assertFailsWith<IOException> { tarInput.nextEntry }
    }

    @Test
    fun truncatedArchiveInContentThrowsWhenSkipping() {
        val full = singleEntryTar(createHeader("afile", 10, 0, false, 493), "0123456789")
        val truncated = full.copyOfRange(0, TarConstants.HEADER_BLOCK + 4)

        val tarInput = TarInput(Buffer().apply { write(truncated) })
        assertNotNull(tarInput.nextEntry)

        assertFailsWith<IOException> { tarInput.nextEntry }
    }

    @Test
    fun equalEntriesShareAHashCodeAndDeduplicateInASet() {
        val one = TarEntry(createHeader("dir/file.txt", 10, 0, false, 493))
        val two = TarEntry(createHeader("dir/file.txt", 10, 0, false, 493))
        val other = TarEntry(createHeader("dir/another.txt", 10, 0, false, 493))

        assertEquals(one, two)
        assertEquals(one.hashCode(), two.hashCode())
        assertEquals(1, setOf(one, two).size)
        assertEquals(2, setOf(one, two, other).size)
    }

    @Test
    fun entrySurvivesAHeaderRoundTripIntoASet() {
        val entry = TarEntry(createHeader("dir/file.txt", 10, 0, false, 493))
        val buf = ByteArray(TarConstants.HEADER_BLOCK)
        entry.writeEntryHeader(buf)

        val parsed = TarEntry(buf)
        assertEquals(entry, parsed)
        assertEquals(entry.hashCode(), parsed.hashCode())
        assertEquals(1, setOf(entry, parsed).size)
    }

    @Test
    fun repeatedLeadingSeparatorsAreFullyTrimmed() {
        assertEquals("a", createHeader("//a", 1, 0, false, 493).name)
        assertEquals("a", createHeader("///a", 1, 0, false, 493).name)
        assertEquals("a/b", createHeader("//a/b//", 1, 0, false, 493).name)
        assertEquals("a", createHeader("\\\\a", 1, 0, false, 493).name)
    }

    @Test
    fun aNameOfOnlySeparatorsDoesNotThrow() {
        assertEquals("", createHeader("///", 1, 0, false, 493).name)
        assertEquals("/", createHeader("///", 0, 0, true, 493).name)
    }

    @Test
    fun aLongNameWithNoSeparatorIsNotSplitAndDoesNotThrow() {
        val longName = "a".repeat(150)
        val header = createHeader(longName, 10, 0, false, 493)

        assertEquals("", header.namePrefix)
        assertEquals(longName, header.name)
    }

    @Test
    fun aLongNameWithASeparatorIsSplitAcrossPrefixAndName() {
        val parent = "d".repeat(120)
        val header = createHeader("$parent/file.txt", 10, 0, false, 493)

        assertEquals(parent, header.namePrefix)
        assertEquals("file.txt", header.name)
        assertEquals("$parent/file.txt", TarEntry(header).name)
    }

    @Test
    fun aLongUnsplittableNameIsTruncatedWhenWritten() {
        val longName = "a".repeat(150)
        val entry = TarEntry(createHeader(longName, 0, 0, false, 493))
        val buf = ByteArray(TarConstants.HEADER_BLOCK)
        entry.writeEntryHeader(buf)

        assertEquals("a".repeat(TarHeader.NAMELEN), TarEntry(buf).name)
    }

    @Test
    fun anEntryEscapingTheDestinationIsRejected() {
        val gz = writeTarGz(createHeader("../evil.txt", 5, 0, false, 493), "pwned")
        val destFolder = dir.resolve("dest")

        assertFailsWith<IOException> { TarGzExpander().expandTarGzFile(gz, destFolder) }
        assertFalse(dir.resolve("evil.txt").exists(), "entry was written outside the destination")
    }

    @Test
    fun anAbsoluteEntryNameIsRejected() {
        // createHeader strips leading slashes, so an absolute name can only arrive from an
        // archive built by another tool.
        val header = TarHeader().apply {
            name = "/tmp/ktar-evil.txt"
            linkFlag = TarHeader.LF_NORMAL
            size = 5
        }

        assertFailsWith<IOException> {
            TarGzExpander().expandTarGzFile(writeTarGz(header, "pwned"), dir.resolve("dest"))
        }
    }

    @Test
    fun aSiblingSharingANamePrefixIsRejected() {
        val gz = writeTarGz(createHeader("../destevil/file.txt", 5, 0, false, 493), "pwned")

        assertFailsWith<IOException> {
            TarGzExpander().expandTarGzFile(gz, dir.resolve("dest"))
        }
    }

    @Test
    fun aNameThatStaysInsideTheDestinationIsAccepted() {
        val gz = writeTarGz(createHeader("sub/../file.txt", 4, 0, false, 493), "fine")
        val destFolder = dir.resolve("dest")

        TarGzExpander().expandTarGzFile(gz, destFolder)

        assertEquals("fine", TestUtils.readFile(destFolder.resolve("file.txt")))
    }

    @Test
    fun writingMoreBytesThanTheEntryDeclaredThrows() {
        val out = TarOutput(Buffer())
        out.putNextEntry(TarEntry(createHeader("afile", 4, 0, false, 493)))

        assertFailsWith<IOException> { out.write("far too much content".encodeToByteArray()) }
    }

    @Test
    fun startingANewEntryBeforeFinishingTheCurrentOneThrows() {
        val out = TarOutput(Buffer())
        out.putNextEntry(TarEntry(createHeader("afile", 10, 0, false, 493)))
        out.write("short".encodeToByteArray())

        assertFailsWith<IOException> {
            out.putNextEntry(TarEntry(createHeader("bfile", 1, 0, false, 493)))
        }
    }

    @Test
    fun anEmptyArchiveReadsBackAsNoEntries() {
        val sink = Buffer()
        TarOutput(sink).close()

        assertEquals(TarConstants.EOF_BLOCK.toLong(), sink.size)
        assertNull(TarInput(sink).nextEntry)
    }

    @Test
    fun octalValuesRoundTrip() {
        // A field spends two of its bytes on a trailing space and NUL, so an 8 byte field holds
        // 6 octal digits, up to 0o777777.
        for (value in listOf(0L, 1L, 7L, 8L, 420L, 493L, 65535L, 262143L)) {
            val buf = ByteArray(TarHeader.MODELEN)
            Octal.getOctalBytes(value, buf, 0, TarHeader.MODELEN)

            assertEquals(value, Octal.parseOctal(buf, 0, TarHeader.MODELEN), "round trip of $value")
        }
    }

    @Test
    fun longOctalValuesRoundTrip() {
        // 11 octal digits, i.e. the classic 8 GiB - 1 tar size limit.
        for (value in listOf(0L, 1L, 512L, 1_048_576L, 8_589_934_591L)) {
            val buf = ByteArray(TarHeader.SIZELEN)
            Octal.getLongOctalBytes(value, buf, 0, TarHeader.SIZELEN)

            assertEquals(value, Octal.parseOctal(buf, 0, TarHeader.SIZELEN), "round trip of $value")
        }
    }

    @Test
    fun anOversizedValueIsRejectedRatherThanTruncated() {
        assertFailsWith<IOException> {
            Octal.getOctalBytes(262_144L, ByteArray(TarHeader.MODELEN), 0, TarHeader.MODELEN)
        }
        assertFailsWith<IOException> {
            Octal.getLongOctalBytes(8_589_934_592L, ByteArray(TarHeader.SIZELEN), 0, TarHeader.SIZELEN)
        }
    }

    @Test
    fun aNegativeValueIsRejected() {
        assertFailsWith<IOException> {
            Octal.getOctalBytes(-1L, ByteArray(TarHeader.MODELEN), 0, TarHeader.MODELEN)
        }
    }

    @Test
    fun anEntryTooLargeForTheSizeFieldIsRejected() {
        val entry = TarEntry(createHeader("huge", 8_589_934_592L, 0, false, 493))

        assertFailsWith<IOException> { entry.writeEntryHeader(ByteArray(TarConstants.HEADER_BLOCK)) }
    }

    @Test
    fun sizeAndModTimeSurviveAHeaderRoundTrip() {
        val size = 1_234_567L
        val modTime = 1_700_000_000L
        val entry = TarEntry(createHeader("afile", size, modTime, false, 493))

        val buf = ByteArray(TarConstants.HEADER_BLOCK)
        entry.writeEntryHeader(buf)
        val parsed = TarEntry(buf)

        assertEquals(size, parsed.size)
        assertEquals(modTime, parsed.header.modTime)
        assertEquals(493, parsed.header.mode)
    }

    @Test
    fun modTimeIsSetFromMilliseconds() {
        val entry = TarEntry(createHeader("afile", 0, 0, false, 493))
        entry.setModTimeMillis(1_700_000_000_000)

        assertEquals(1_700_000_000L, entry.header.modTime)
    }

    @Test
    fun directoryEntriesAreRecognisedFromTheHeader() {
        assertTrue(TarEntry(createHeader("adir", 0, 0, true, 493)).isDirectory)
        assertFalse(TarEntry(createHeader("afile", 1, 0, false, 493)).isDirectory)
    }

    @Test
    fun calculateTarSizePredictsTheWrittenArchiveSize() {
        val source = dir.resolve("src")
        FileSystem.SYSTEM.createDirectories(source)
        TestUtils.writeStringToFile("hello", source.resolve("one"))
        TestUtils.writeStringToFile("a somewhat longer file", source.resolve("two"))

        val tarPath = dir.resolve("out.tar")
        TarOutput(tarPath).use { out ->
            for (file in source.list().sortedBy { it.name }) {
                out.putNextEntry(TarEntry(file, "src/${file.name}"))
                out.write(FileSystem.SYSTEM.read(file) { readByteArray() })
            }
        }

        assertEquals(TarUtils.calculateTarSize(source), tarPath.length())
    }

    private fun singleEntryTar(header: TarHeader, content: String): ByteArray {
        val sink = Buffer()
        val out = TarOutput(sink)
        out.putNextEntry(TarEntry(header))
        out.write(content.encodeToByteArray())
        out.close()
        return sink.readByteArray()
    }

    private fun writeTarGz(header: TarHeader, content: String): Path {
        val tar = singleEntryTar(header, content)
        val gzPath = dir.resolve("archive.tar.gz")

        FileSystem.SYSTEM.sink(gzPath).use { fileSink ->
            GzipSink(fileSink).buffer().use { it.write(tar) }
        }

        return gzPath
    }
}
