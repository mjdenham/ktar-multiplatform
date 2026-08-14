package org.martin.ktar

import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Convenience extensions over [FileSystem.SYSTEM], for the common case of working with real files.
 *
 * They all operate on the default system file system; use [FileSystem] directly if you need to
 * work against another one.
 */

/** True if this path exists on the system file system. */
public fun Path.exists(): Boolean = FileSystem.SYSTEM.exists(this)

/** True if this path is a directory. Throws if the path does not exist. */
public fun Path.isDirectory(): Boolean = FileSystem.SYSTEM.metadata(this).isDirectory

/** True if this path is a regular file. Throws if the path does not exist. */
public fun Path.isFile(): Boolean = FileSystem.SYSTEM.metadata(this).isRegularFile

/** The immediate children of this directory. Throws if the path is not a directory. */
public fun Path.list(): List<Path> = FileSystem.SYSTEM.list(this)

/** The size of this file in bytes, or 0 if the size is unknown. */
public fun Path.length(): Long = FileSystem.SYSTEM.metadata(this).size ?: 0
