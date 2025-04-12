package org.martin.ktar

import okio.Path

object TarUtils {
    /**
     * Determines the tar file size of the given folder/file path
     */
	fun calculateTarSize(path: Path): Long {
        return tarSize(path) + TarConstants.EOF_BLOCK
    }

    private fun tarSize(dir: Path): Long {
        var size: Long = 0

        if (dir.isFile()) {
            return entrySize(dir.length())
        } else {
            val subFiles = dir.list()

            if (subFiles.isNotEmpty()) {
                for (file in subFiles) {
                    size += if (file.isFile()) {
                        entrySize(file.length())
                    } else {
                        tarSize(file)
                    }
                }
            } else {
                // Empty folder header
                return TarConstants.HEADER_BLOCK.toLong()
            }
        }

        return size
    }

    private fun entrySize(fileSize: Long): Long {
        var size: Long = 0
        size += TarConstants.HEADER_BLOCK.toLong() // Header
        size += fileSize // File size

        val extra = size % TarConstants.DATA_BLOCK

        if (extra > 0) {
            size += (TarConstants.DATA_BLOCK - extra) // pad
        }

        return size
    }

	fun trim(s: String, c: Char): String {
        val tmp = StringBuilder(s)
        for (i in 0..<tmp.length) {
            if (tmp[i] != c) {
                break
            } else {
                tmp.deleteAt(i)
            }
        }

        for (i in tmp.length - 1 downTo 0) {
            if (tmp[i] != c) {
                break
            } else {
                tmp.deleteAt(i)
            }
        }

        return tmp.toString()
    }
}
