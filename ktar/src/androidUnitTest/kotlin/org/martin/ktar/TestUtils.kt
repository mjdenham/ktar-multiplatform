package org.martin.ktar

import okio.FileSystem
import okio.Path

object TestUtils {
    fun writeStringToFile(string: String, file: Path): Path {
        FileSystem.SYSTEM.write(file) {
            writeUtf8(string)
        }
        return file
    }

    fun readFile(file: Path): String {
        FileSystem.SYSTEM.read(file) {
            return this.readUtf8()
        }
    }
}
