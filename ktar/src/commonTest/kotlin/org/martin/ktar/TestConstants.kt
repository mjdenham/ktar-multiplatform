package org.martin.ktar

import okio.Path.Companion.toPath

/**
 * Fixture archives live in a `testFiles` folder at the repository root, so that they are reachable
 * from every test target rather than being tied to one source set's resources. Paths are relative
 * to the module directory, which is the working directory the test tasks run in.
 */
object TestConstants {
    val TAR_TEST_FILE = "../testFiles/tartest.tar".toPath()
    val CROSSWIRE_TAR_FILE = "../testFiles/mods.d.tar".toPath()
    val CROSSWIRE_TAR_GZ_FILE = "../testFiles/mods.d.tar.gz".toPath()
}
