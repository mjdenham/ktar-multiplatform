package org.martin.ktar

import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random

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

    /**
     * Creates an empty scratch directory with a name unique to this run.
     *
     * The suite runs against several targets at once (jvm and androidHostTest at least), so a
     * fixed directory name under the system temp folder would have those runs deleting each
     * other's files midway through a test.
     */
    fun createUniqueTestDir(name: String): Path {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
            .resolve("ktar-test")
            .resolve("$name-${Random.nextInt(0, Int.MAX_VALUE)}")
        FileSystem.SYSTEM.createDirectories(dir)
        return dir
    }
}
