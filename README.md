# ktar

KTar is a Kotlin Multiplatform library to read and write tar files using [okio](https://square.github.io/okio/).
This library is derived from the [jtar](https://github.com/kamranzafar/jtar) library.

It works with `okio.Path`, `okio.BufferedSource` and `okio.BufferedSink`, so it has no dependency on
`java.io` and runs unchanged on Android, the JVM and iOS.

## Features

- Read tar archives entry by entry (`TarInput`)
- Write tar archives entry by entry (`TarOutput`)
- Expand `.tar.gz` archives to disk, or stream their contents into memory (`TarGzExpander`)
- ustar (POSIX) headers, including the filename prefix field for long paths
- UTF-8 entry names, truncated on character boundaries so code points are never split

## Supported platforms

| Target | Notes |
| --- | --- |
| Android | `minSdk 24`, compiled for JVM 1.8 |
| JVM | compiled for JVM 17 |
| iOS | `iosArm64` and `iosSimulatorArm64`, published as a static `ktar` XCFramework |

## Installation

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mjdenham:ktar:0.1.0")
        }
    }
}
```

okio is exposed as an `api` dependency, so `Path`, `Buffer` and friends are available to you without
declaring okio separately.

## Usage

### Expand a .tar.gz file to a folder

The highest-level entry point. Creates the destination folder and any intermediate directories held
in the archive.

```kotlin
import okio.Path.Companion.toPath
import org.martin.ktar.TarGzExpander

TarGzExpander().expandTarGzFile(
    tarGzFile = "/downloads/mods.d.tar.gz".toPath(),
    destFolder = "/data/modules".toPath(),
)
```

### Read .tar.gz contents without writing to disk

`handleTarGzContent` invokes your lambda once per file entry with the entry name and an
`okio.Buffer` holding its content. Directory entries are skipped.

```kotlin
TarGzExpander().handleTarGzContent("/downloads/mods.d.tar.gz".toPath()) { name, content ->
    if (name.endsWith(".conf")) {
        println("$name:\n${content.readUtf8()}")
    }
}
```

The buffer is only valid inside the lambda — read what you need before returning.

### Read a tar archive

`nextEntry` advances to the next entry and returns `null` at the end of the archive. `read` returns
`-1` once the current entry has been fully consumed, so the inner loop stops at the entry boundary.
Any unread bytes are skipped automatically when you advance to the next entry.

```kotlin
import okio.FileSystem
import okio.SYSTEM
import okio.buffer
import okio.use
import org.martin.ktar.TarEntry
import org.martin.ktar.TarInput

val source = FileSystem.SYSTEM.source("/downloads/archive.tar".toPath()).buffer()

TarInput(source).use { tarInput ->
    val data = ByteArray(2048)
    var entry: TarEntry? = tarInput.nextEntry
    while (entry != null) {
        if (entry.isDirectory) {
            FileSystem.SYSTEM.createDirectories(destFolder.resolve(entry.name))
        } else {
            FileSystem.SYSTEM.sink(destFolder.resolve(entry.name)).buffer().use { dest ->
                var count: Int
                while (tarInput.read(data).also { count = it } != -1) {
                    dest.write(data, 0, count)
                }
            }
        }
        entry = tarInput.nextEntry
    }
}
```

Note that entry names may contain directories that do not appear as their own entries — create the
parent of each file before writing it if the archive may be built that way.

### Write a tar archive

Call `putNextEntry` then write exactly `entry.size` bytes before moving to the next entry. Closing
the `TarOutput` pads the final block and appends the EOF record, so the archive is only valid if you
close it.

```kotlin
import okio.use
import org.martin.ktar.TarEntry
import org.martin.ktar.TarOutput

TarOutput("/tmp/archive.tar".toPath()).use { tar ->
    for (file in filesToArchive) {
        tar.putNextEntry(TarEntry(file, file.name))

        FileSystem.SYSTEM.source(file).buffer().use { source ->
            val buffer = ByteArray(2048)
            var length: Int
            while (source.read(buffer).also { length = it } > 0) {
                tar.write(buffer, 0, length)
            }
        }
    }
}
```

`TarOutput` throws an `IOException` if you write more bytes than the entry declared, or if you start
a new entry before the current one has been fully written.

### Build entries that have no file on disk

`TarHeader.createHeader` lets you add entries programmatically:

```kotlin
import org.martin.ktar.TarEntry
import org.martin.ktar.TarHeader.Companion.createHeader

val header = createHeader(
    entryName = "generated/notes.txt",
    size = content.size.toLong(),
    modTime = epochSeconds,   // seconds, not millis
    dir = false,
    permissions = 493,        // octal 0755
)
tar.putNextEntry(TarEntry(header))
tar.write(content)
```

### Predict the size of an archive

```kotlin
import org.martin.ktar.TarUtils.calculateTarSize

val expectedBytes = calculateTarSize(folderToArchive)
```

## API summary

| Type | Purpose |
| --- | --- |
| `TarInput(BufferedSource)` | Reads entries; `nextEntry`, `read`, `currentOffset`, `isDefaultSkip` |
| `TarOutput(BufferedSink)` / `TarOutput(Path)` | Writes entries; `putNextEntry`, `write`, `flush`, `close` |
| `TarEntry` | One archive entry; `name`, `size`, `isDirectory`, `userId`/`groupId`, `header` |
| `TarHeader` | The raw ustar header fields, plus `createHeader` |
| `TarGzExpander` | `expandTarGzFile`, `handleTarGzContent` |
| `TarUtils` | `calculateTarSize` |
| `TarConstants` | `HEADER_BLOCK` (512), `DATA_BLOCK` (512), `EOF_BLOCK` (1024) |

`TarInput.currentOffset` reports the byte offset from the start of the stream, which is useful for
recording where an entry's content begins. Setting `isDefaultSkip = true` makes skipping unread
entry bytes delegate to okio's `skip` instead of reading and discarding them.

## Limitations

- **Permissions.** okio has no file permission API, so entries created from a `Path` are written with
  a fixed mode of read access (`PermissionUtils.defaultOkioPermissions`). Pass an explicit mode to
  `TarHeader.createHeader` if you need something else. Permissions are not restored when extracting.
- **Owner names.** There is no multiplatform equivalent of `user.name`, so `userName` defaults to
  empty and `userId`/`groupId` default to `0`. Set them on the entry if you need them populated.
- **Long names.** Names over 100 characters are split into the ustar filename prefix and name
  fields. Names whose UTF-8 encoding exceeds the 100-byte name field are truncated at a character
  boundary rather than failing. GNU and PAX long-name extensions are not implemented.
- **Entry types.** Only regular files and directories are handled by the `TarGzExpander` helpers.
  Symlinks, hard links and device nodes are parsed into the header but not recreated.
- **Untrusted archives.** Entry names are used as-is when resolving output paths — nothing in ktar
  rejects names containing `..`. Validate that each entry resolves inside your destination directory
  before extracting archives you did not create.

## Building

```bash
./gradlew build          # compile all targets and run tests
./gradlew allTests       # tests only
```

Tests live in `ktar/src/androidHostTest` and run on the JVM against real archive fixtures in
`ktar/src/androidHostTest/resources`.

## Licence

GNU Lesser General Public License, version 2.1 — see [LICENSE](LICENSE).
Derived from [jtar](https://github.com/kamranzafar/jtar) by Kamran Zafar.
