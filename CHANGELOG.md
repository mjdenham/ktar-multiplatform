# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/mjdenham/ktar-multiplatform/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/mjdenham/ktar-multiplatform/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/mjdenham/ktar-multiplatform/releases/tag/v0.1.0
