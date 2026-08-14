package org.martin.ktar

import okio.BufferedSink
import okio.Closeable
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.IOException
import okio.SYSTEM

/**
 * Writes a tar archive to [out], one entry at a time.
 *
 * Call [putNextEntry], then [write] exactly `entry.size` bytes before moving on. Closing pads the
 * final block and appends the EOF record, so the archive is only valid once it has been closed.
 */
public class TarOutput(private val out: BufferedSink) : Closeable {
    private var bytesWritten: Long = 0
    private var currentFileSize: Long = 0
    private var currentEntry: TarEntry? = null

    /** Writes the archive to [fout] on the system file system. */
    public constructor(fout: Path) : this(FileSystem.SYSTEM.sink(fout).buffer())

    /**
     * Appends the EOF record and closes the stream
     */
    override fun close() {
        closeCurrentEntry()
        write(ByteArray(TarConstants.EOF_BLOCK))
        out.close()
    }

    /** Flushes any buffered bytes to the underlying sink. */
    public fun flush(): Unit = out.flush()

    /**
     * Writes [len] bytes of the current entry's content from [b] at [off].
     *
     * @throws IOException if this would write more bytes than the current entry declared.
     */
    public fun write(b: ByteArray, off: Int = 0, len: Int = b.size) {
        currentEntry?.let { entry ->
            if (!entry.isDirectory && entry.size < currentFileSize + len) {
                throw IOException(
                    "The current entry[${entry.name}] size[${entry.size}] is smaller than the bytes[${currentFileSize + len}] being written."
                )
            }
        }

        out.write(b, off, len)

        bytesWritten += len.toLong()

        if (currentEntry != null) {
            currentFileSize += len.toLong()
        }
    }

    /**
     * Closes the previous entry and writes [entry]'s header block.
     *
     * @throws IOException if the previous entry has not been fully written.
     */
    public fun putNextEntry(entry: TarEntry) {
        closeCurrentEntry()

        val header = ByteArray(TarConstants.HEADER_BLOCK)
        entry.writeEntryHeader(header)

        write(header)

        currentEntry = entry
    }

    /**
     * Closes the current tar entry
     */
    private fun closeCurrentEntry() {
        currentEntry?.let { entry ->
            if (entry.size > currentFileSize) {
                throw IOException(
                    "The current entry[${entry.name}] of size[${entry.size}] has not been fully written."
                )
            }

            currentEntry = null
            currentFileSize = 0

            pad()
        }
    }

    /**
     * Pads the last content block
     */
    private fun pad() {
        if (bytesWritten > 0) {
            val extra = (bytesWritten % TarConstants.DATA_BLOCK).toInt()

            if (extra > 0) {
                write(ByteArray(TarConstants.DATA_BLOCK - extra))
            }
        }
    }
}
