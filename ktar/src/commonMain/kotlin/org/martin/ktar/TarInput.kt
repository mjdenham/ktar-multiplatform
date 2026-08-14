package org.martin.ktar

import okio.BufferedSource
import okio.Closeable
import okio.IOException

/**
 * Reads a tar archive from [inputSource], one entry at a time.
 *
 * Advance with [nextEntry], then [read] the current entry's content until it returns -1. Any
 * bytes left unread are skipped when you advance to the next entry.
 */
public class TarInput(private val inputSource: BufferedSource): Closeable {
    private var currentEntry: TarEntry? = null
    private var currentFileSize: Long = 0

    /**
     * Returns the current offset (in bytes) from the beginning of the stream.
     * This can be used to find out at which point in a tar file an entry's content begins, for instance.
     */
    public var currentOffset: Long = 0
        private set

    /**
     * When true, skipping unread entry bytes delegates to the source's own skip rather than
     * reading and discarding them.
     */
    public var isDefaultSkip: Boolean = false

    override fun close(): Unit = inputSource.close()

    /**
     * Reads up to [len] bytes of the current entry's content into [b] at [off].
     *
     * Reads never cross an entry boundary: the length is clamped to what remains of the current
     * entry, and -1 is returned once the entry has been fully consumed.
     *
     * @return the number of bytes read, or -1 at the end of the current entry.
     */
    public fun read(b: ByteArray, off: Int = 0, len: Int = b.size): Int {
        var adjustedLen = len
        currentEntry?.let { entry ->
            if (currentFileSize == entry.size) {
                return -1
            } else if ((entry.size - currentFileSize) < adjustedLen) {
                adjustedLen = (entry.size - currentFileSize).toInt()
            }
        }

        val br = inputSource.read(b, off, adjustedLen)

        if (br != -1) {
            if (currentEntry != null) {
                currentFileSize += br.toLong()
            }

            currentOffset += br.toLong()
        }

        return br
    }

    /**
     * The next entry in the archive, or null once the end of the archive is reached.
     *
     * Reading this advances the stream: it skips any unread content of the current entry and
     * then parses the next header block.
     *
     * @throws IOException if the archive appears to be truncated or corrupt.
     */
    public val nextEntry: TarEntry?
        get() {
            closeCurrentEntry()

            val header = ByteArray(TarConstants.HEADER_BLOCK)
            val tHeader = ByteArray(TarConstants.HEADER_BLOCK)
            var tr = 0

            // Read full header
            while (tr < TarConstants.HEADER_BLOCK) {
                val res = read(tHeader, 0, TarConstants.HEADER_BLOCK - tr)

                if (res < 0) {
                    break
                }

                tHeader.copyInto(header, destinationOffset = tr, startIndex = 0, endIndex = res)
                tr += res
            }

            val eof = header.all { it.toInt() == 0 }

            if (!eof) {
                currentEntry = TarEntry(header)
            }

            return currentEntry
        }

    /**
     * Closes the current tar entry
     */
    private fun closeCurrentEntry() {
        currentEntry?.let { entry ->
            val remaining = entry.size - currentFileSize
            if (remaining > 0) {
                // Not fully read, skip rest of the bytes
                var bs: Long = 0
                while (bs < remaining) {
                    val res = skip(remaining - bs)

                    if (res == 0L) {
                        // I suspect file corruption
                        throw IOException("Possible tar file corruption")
                    }

                    bs += res
                }
            }

            currentEntry = null
            currentFileSize = 0L
            skipPad()
        }
    }

    /**
     * Skips the pad at the end of each tar entry file content
     */
    private fun skipPad() {
        if (currentOffset > 0) {
            val extra = (currentOffset % TarConstants.DATA_BLOCK).toInt()

            if (extra > 0) {
                var bs: Long = 0
                while (bs < TarConstants.DATA_BLOCK - extra) {
                    val res = skip(TarConstants.DATA_BLOCK - extra - bs)

                    if (res == 0L) {
                        // Skipping makes no progress at end of stream, so without this the
                        // loop would spin forever on a truncated archive.
                        throw IOException("Possible tar file corruption")
                    }

                    bs += res
                }
            }
        }
    }

    /**
     * Skips [n] bytes of the current entry, either by delegating to the source's own skip when
     * [isDefaultSkip] is set, or by reading and discarding.
     *
     * @return the number of bytes actually skipped, which is less than [n] at end of stream.
     */
    private fun skip(n: Long): Long {
        if (isDefaultSkip) {
            // use skip method of parent stream
            // may not work if skip not implemented by parent
            inputSource.skip(n)
            currentOffset += n

            return n
        }

        if (n <= 0) {
            return 0
        }

        var left = n
        val sBuff = ByteArray(SKIP_BUFFER_SIZE)

        while (left > 0) {
            val res = read(sBuff, 0, (if (left < SKIP_BUFFER_SIZE) left else SKIP_BUFFER_SIZE.toLong()).toInt())
            if (res < 0) {
                break
            }
            left -= res.toLong()
        }

        return n - left
    }

    private companion object {
        const val SKIP_BUFFER_SIZE = 2048
    }
}
