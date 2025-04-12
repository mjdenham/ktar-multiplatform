package org.martin.ktar

import okio.FileSystem
import okio.GzipSource
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use

class TarGzExpander {

    fun expandTarGzFile(tarGzFile: Path, destFolder: Path) {
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
        var entry: TarEntry?
        val data = ByteArray(BUFFER)

        while (tis.nextEntry.also { entry = it } != null) {
            if (entry!!.isDirectory) {
                FileSystem.SYSTEM.createDirectories(destFolder.resolve(entry!!.name))
                continue
            } else {
                val di = entry!!.name.lastIndexOf('/')
                if (di != -1) {
                    FileSystem.SYSTEM.createDirectories(destFolder.resolve(entry!!.name.substring(0, di)))
                }
            }

            val outPath = destFolder.resolve(entry!!.name)
            FileSystem.SYSTEM.sink(outPath).buffer().use { dest ->
                var count: Int
                while ((tis.read(data).also { count = it }) != -1) {
                    dest.write(data, 0, count)
                }
                dest.flush()
            }
        }
    }

    companion object {
        const val BUFFER: Int = 2048
    }
}