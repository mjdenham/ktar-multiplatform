# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-08-13

A tidy-up release. It fixes several defects inherited from the original jtar port, removes
Java-isms from the public API, and gets the test suite running on the JVM as well as Android.

### Fixed

- `TarInput` no longer loops forever when an archive is truncated part way through an entry's
  block padding. Skipping makes no progress at the end of a stream, so the pad loop never
  terminated; it now reports corruption in the same way the entry-content loop already did.
- `TarEntry.equals` and `hashCode` now agree. `hashCode` was derived from a `StringBuilder`, which
  does not override it, so equal entries produced different hash codes and could not be used in a
  `HashSet` or `HashMap`.
- Entry names with repeated leading separators are trimmed correctly. The old hand-written trim
  deleted while iterating forwards, so `//a` became `/a` and a name of only separators threw.
- `TarHeader.createHeader` no longer throws for a name longer than 100 characters that contains no
  `/`. Such a name cannot be split across the ustar prefix and name fields, so it is left whole and
  truncated when written.
- Values too large for their header field are rejected instead of being silently written as their
  truncated low-order digits, which produced a corrupt header. In particular an entry of 8 GiB or
  more now fails rather than recording a wrong size.

### Security

- `TarGzExpander.expandTarGzFile` rejects entries that resolve outside the destination folder
  ("zip slip"). Previously entry names were used as-is, so an archive containing `../` names could
  write anywhere the process could reach. This was documented as a limitation rather than enforced.

### Changed

- **Breaking.** `TarHeader`'s `name`, `linkName`, `magic`, `userName`, `groupName` and `namePrefix`
  are now `String` rather than `StringBuilder`.
- **Breaking.** Implementation detail is no longer public: `Octal`, `TarHeader.parseName`,
  `TarHeader.getNameBytes`, and `TarEntry.computeCheckSum` / `writeEntryHeader` / `parseTarHeader`
  are now `internal`. `TarEntry.extractTarHeader` and `TarUtils.trim` have been removed;
  `TarUtils.trim` is replaced by the standard library's `String.trim(Char)`.
- **Breaking.** `TarEntry.setModTime` is renamed `setModTimeMillis`. It always took milliseconds
  while `TarHeader.createHeader` takes seconds, which was easy to get wrong silently.
- **Breaking.** `TarEntry.file` and `TarEntry.header` are now `val` rather than `var` with a
  `protected` setter, which had no effect on a final class.
- Explicit API mode is enabled, so the published API surface is now stated rather than inferred.
- `TarGzExpander` and the rest of the public API carry KDoc; several inherited doc comments
  described return values the functions never had.

`TarGzExpander`, `TarInput`, `TarOutput`, `TarUtils.calculateTarSize` and the `okio.Path`
extensions are unchanged, so ordinary use of the library is unaffected.

### Internal

- Tests moved from `androidHostTest` to `commonTest`. They now compile for every target and run on
  both the JVM and the Android host, where previously only the Android host ran them and the JVM
  and iOS targets shipped untested. Fixtures moved to `testFiles/` at the repository root.
- Test coverage grew from 10 to 35 cases, covering each fix above plus octal encoding, the
  `TarOutput` error paths and long-name splitting.
- The publish plugin moved into the version catalog and was updated from 0.30.0 to 0.37.0. The
  published version is now set once, via `VERSION_NAME` in `gradle.properties`.

## [0.1.1] - 2026-07-29

No source changes. Rebuilt on a newer toolchain and dependency set.

### Changed

- Updated okio to 3.18.1 (from 3.17.0). okio is an `api` dependency, so this is
  visible to consumers.
- Updated Kotlin to 2.4.10, Android Gradle Plugin to 9.3.1 and the Gradle wrapper
  to 9.6.1.
- Expanded the README with usage examples, an API summary and a list of known
  limitations.

## [0.1.0] - 2026-07-18

Initial release to Maven Central as `io.github.mjdenham:ktar`.

### Added

- `TarInput` — read tar archives entry by entry from an okio `BufferedSource`,
  with `currentOffset` reporting and optional `isDefaultSkip` behaviour.
- `TarOutput` — write tar archives to an okio `BufferedSink` or `Path`, with
  block padding and the EOF record handled on close.
- `TarEntry` and `TarHeader` — ustar (POSIX) header parsing and generation,
  including the filename prefix field for long paths.
- `TarGzExpander` — expand a `.tar.gz` to a folder, or stream each entry's
  contents into memory via `handleTarGzContent`.
- `TarUtils.calculateTarSize` — predict the size of an archive before writing it.
- UTF-8 entry names, truncated on character boundaries so code points are never
  split across the 100-byte name field.
- Targets: JVM (17), Android (`minSdk 24`) and iOS (`iosArm64`,
  `iosSimulatorArm64`).

[Unreleased]: https://github.com/mjdenham/ktar-multiplatform/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/mjdenham/ktar-multiplatform/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/mjdenham/ktar-multiplatform/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/mjdenham/ktar-multiplatform/releases/tag/v0.1.0
