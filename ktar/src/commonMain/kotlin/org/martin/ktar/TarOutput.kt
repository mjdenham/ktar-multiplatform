package org.martin.ktar

import okio.BufferedSink
import okio.Closeable
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.IOException
import okio.SYSTEM

class TarOutput : Closeable {
    private val out: BufferedSink
    private var bytesWritten: Long = 0
    private var currentFileSize: Long = 0
    private var currentEntry: TarEntry? = null

    constructor(out: BufferedSink) {
        this.out = out
        bytesWritten = 0
        currentFileSize = 0
    }

    constructor(fout: Path) {
        this.out = FileSystem.SYSTEM.sink(fout).buffer()
        bytesWritten = 0
        currentFileSize = 0
    }

    /**
     * Appends the EOF record and closes the stream
     */
    override fun close() {
        closeCurrentEntry()
        write(ByteArray(TarConstants.EOF_BLOCK))
        out.close()
    }

    fun flush() = out.flush()

    /**
     * Checks if the bytes being written exceed the current entry size.
     */
    fun write(b: ByteArray, off: Int = 0, len: Int = b.size) {
        if (currentEntry != null && !currentEntry!!.isDirectory) {
            if (currentEntry!!.size < currentFileSize + len) {
                throw IOException(
                    ("The current entry[${currentEntry!!.name}] size[${currentEntry!!.size}] is smaller than the bytes[${currentFileSize + len}] being written.")
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
     * Writes the next tar entry header on the stream
     */
    fun putNextEntry(entry: TarEntry) {
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
        if (currentEntry != null) {
            if (currentEntry!!.size > currentFileSize) {
                throw IOException(
                    ("The current entry[" + currentEntry!!.name + "] of size["
                            + currentEntry!!.size + "] has not been fully written.")
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
