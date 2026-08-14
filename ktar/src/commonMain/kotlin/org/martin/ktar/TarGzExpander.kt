package org.martin.ktar

import okio.Buffer
import okio.FileSystem
import okio.GzipSource
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

/**
 * Expands `.tar.gz` archives, either to disk or into memory.
 *
 * Only regular files and directories are handled; symlinks, hard links and device nodes are
 * parsed into the header but not recreated.
 */
public class TarGzExpander {

    /**
     * Streams the content of each file entry in [tarGzFile] into memory, invoking
     * [contentHandler] once per entry with the entry name and a buffer holding its content.
     * Directory entries are skipped.
     *
     * The buffer is only valid for the duration of the call — read what you need before returning.
     */
    public fun handleTarGzContent(tarGzFile: Path, contentHandler: (String, Buffer) -> Unit) {
        FileSystem.SYSTEM.source(tarGzFile).buffer().use { tarGzSource ->
            GzipSource(tarGzSource).buffer().use { tarSource ->
                TarInput(tarSource).use { tis ->
                    val data = ByteArray(BUFFER)
                    var entry: TarEntry? = tis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name

                            val outBuffer = Buffer()
                            outBuffer.use { dest ->
                                var count: Int
                                while ((tis.read(data).also { count = it }) != -1) {
                                    outBuffer.write(data, 0, count)
                                }
                                dest.flush()
                                contentHandler(name, outBuffer)
                            }
                        }

                        entry = tis.nextEntry
                    }
                }
            }
        }
    }

    /**
     * Expands [tarGzFile] into [destFolder], creating the destination and any intermediate
     * directories held in the archive.
     *
     * @throws IOException if an entry name would resolve outside [destFolder], or if the archive
     * is truncated or corrupt.
     */
    public fun expandTarGzFile(tarGzFile: Path, destFolder: Path) {
        FileSystem.SYSTEM.createDirectories(destFolder)
        FileSystem.SYSTEM.source(tarGzFile).buffer().use { tarGzSource ->
            GzipSource(tarGzSource).buffer().use { tarSource ->
                TarInput(tarSource).use { tarInput ->
                    untar(tarInput, destFolder)
                }
            }
        }
    }

    private fun untar(tis: TarInput, destFolder: Path) {
        val data = ByteArray(BUFFER)
        var entry: TarEntry? = tis.nextEntry
        while (entry != null) {
            val outPath = resolveSafely(destFolder, entry.name)
            if (entry.isDirectory) {
                FileSystem.SYSTEM.createDirectories(outPath)
            } else {
                outPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
                FileSystem.SYSTEM.sink(outPath).buffer().use { dest ->
                    var count: Int
                    while ((tis.read(data).also { count = it }) != -1) {
                        dest.write(data, 0, count)
                    }
                    dest.flush()
                }
            }
            entry = tis.nextEntry
        }
    }

    /**
     * Entry names are attacker controlled in any archive you did not create, so a name such as
     * `../../etc/passwd` must not be allowed to write outside [destFolder].
     */
    private fun resolveSafely(destFolder: Path, entryName: String): Path {
        val base = destFolder.normalized()
        val resolved = base.resolve(entryName).normalized()

        if (resolved != base && !resolved.segments.startsWithSegmentsOf(base)) {
            throw IOException("Tar entry '$entryName' would be extracted outside of $destFolder")
        }

        return resolved
    }

    /**
     * Compared segment by segment rather than as strings, so that a sibling sharing a name prefix
     * (`/tmp/foobar` against a base of `/tmp/foo`) is not mistaken for a child.
     */
    private fun List<String>.startsWithSegmentsOf(base: Path): Boolean {
        val baseSegments = base.segments
        return size > baseSegments.size && subList(0, baseSegments.size) == baseSegments
    }

    private companion object {
        const val BUFFER: Int = 2048
    }
}
