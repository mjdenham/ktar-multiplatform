package org.martin.ktar

import okio.BufferedSource
import okio.Closeable
import okio.IOException

class TarInput(private val inputSource: BufferedSource): Closeable {
    private var currentEntry: TarEntry? = null
    private var currentFileSize: Long = 0

    /**
     * Returns the current offset (in bytes) from the beginning of the stream.
     * This can be used to find out at which point in a tar file an entry's content begins, for instance.
     */
    var currentOffset: Long = 0
        private set
    var isDefaultSkip: Boolean = false

    override fun close() = inputSource.close()

    /**
     * Checks if the bytes being read exceed the entry size and adjusts the byte
     * array length. Updates the byte counters
     */
    fun read(b: ByteArray, off: Int = 0, len: Int = b.size): Int {
        var adjustedLen = len
        if (currentEntry != null) {
            if (currentFileSize == currentEntry!!.size) {
                return -1
            } else if ((currentEntry!!.size - currentFileSize) < adjustedLen) {
                adjustedLen = (currentEntry!!.size - currentFileSize).toInt()
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

    val nextEntry: TarEntry?
        /**
         * Returns the next entry in the tar file
         *
         * @return TarEntry
         * @throws IOException
         */
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

            // Check if record is null
            var eof = true
            for (b in header) {
                if (b.toInt() != 0) {
                    eof = false
                    break
                }
            }

            if (!eof) {
                currentEntry = TarEntry(header)
            }

            return currentEntry
        }

    /**
     * Closes the current tar entry
     */
    private fun closeCurrentEntry() {
        if (currentEntry != null) {
            if (currentEntry!!.size > currentFileSize) {
                // Not fully read, skip rest of the bytes
                var bs: Long = 0
                while (bs < currentEntry!!.size - currentFileSize) {
                    val res = skip(currentEntry!!.size - currentFileSize - bs)

                    if (res == 0L && currentEntry!!.size - currentFileSize > 0) {
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
                    bs += res
                }
            }
        }
    }

    /**
     * Skips 'n' bytes on the InputStream<br></br>
     * Overrides default implementation of skip
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

    companion object {
        private const val SKIP_BUFFER_SIZE = 2048
    }
}
