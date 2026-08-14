package org.martin.ktar

import okio.Path

public object TarUtils {
    /**
     * Determines the size of the tar archive that would be produced for the given file or folder,
     * including per-entry headers, block padding and the trailing EOF record.
     */
    public fun calculateTarSize(path: Path): Long {
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
}
