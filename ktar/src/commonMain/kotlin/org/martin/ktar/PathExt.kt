package org.martin.ktar

import okio.FileSystem
import okio.Path
import okio.SYSTEM

fun Path.isDirectory() = FileSystem.SYSTEM.metadata(this).isDirectory
fun Path.isFile() = FileSystem.SYSTEM.metadata(this).isRegularFile
fun Path.list() = FileSystem.SYSTEM.list(this)
fun Path.length() = FileSystem.SYSTEM.metadata(this).size ?: 0