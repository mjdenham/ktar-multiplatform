package org.martin.ktar

/**
 * The raw fields of a ustar (POSIX) tar header block.
 *
 * The header occupies a single 512 byte block laid out as follows:
 *
 * ```
 * Offset  Size     Field
 * 0       100      File name
 * 100     8        File mode
 * 108     8        Owner's numeric user ID
 * 116     8        Group's numeric user ID
 * 124     12       File size in bytes
 * 136     12       Last modification time in numeric Unix time format
 * 148     8        Checksum for header block
 * 156     1        Link indicator (file type)
 * 157     100      Name of linked file
 * ```
 *
 * The link indicator identifies the entry type:
 *
 * ```
 * Value        Meaning
 * '0'          Normal file
 * (ASCII NUL)  Normal file (now obsolete)
 * '1'          Hard link
 * '2'          Symbolic link
 * '3'          Character special
 * '4'          Block special
 * '5'          Directory
 * '6'          FIFO
 * '7'          Contiguous
 * ```
 *
 * The ustar extension adds the following fields, including the filename prefix that allows
 * paths longer than the 100 byte name field:
 *
 * ```
 * Offset  Size    Field
 * 257     6       UStar indicator "ustar"
 * 263     2       UStar version "00"
 * 265     32      Owner user name
 * 297     32      Owner group name
 * 329     8       Device major number
 * 337     8       Device minor number
 * 345     155     Filename prefix
 * ```
 */
public class TarHeader {
    /** Excludes [namePrefix], which holds the leading portion of a long path. */
    public var name: String = ""

    /** Permission bits, as an octal value. */
    public var mode: Int = 0

    /** Defaults to 0: okio exposes no owner information. */
    public var userId: Int = 0

    /** Defaults to 0: okio exposes no owner information. */
    public var groupId: Int = 0

    /** Always 0 for directories. */
    public var size: Long = 0

    /** Last modification time, in **seconds** since the Unix epoch. */
    public var modTime: Long = 0

    /** Populated when the header is written, but not verified on read. */
    public var checkSum: Int = 0

    /** The entry type, e.g. [LF_NORMAL] or [LF_DIR]. */
    public var linkFlag: Byte = 0

    public var linkName: String = ""

    public var magic: String = USTAR_MAGIC

    /** Defaults to empty: okio exposes no owner information. */
    public var userName: String = ""

    /** Defaults to empty: okio exposes no owner information. */
    public var groupName: String = ""

    public var devMajor: Int = 0

    public var devMinor: Int = 0

    /** The leading portion of a path too long for the [NAMELEN] byte [name] field. */
    public var namePrefix: String = ""

    public companion object {
        /*
         * Header
         */
        public const val NAMELEN: Int = 100
        public const val MODELEN: Int = 8
        public const val UIDLEN: Int = 8
        public const val GIDLEN: Int = 8
        public const val SIZELEN: Int = 12
        public const val MODTIMELEN: Int = 12
        public const val CHKSUMLEN: Int = 8
        public const val LF_OLDNORM: Byte = 0

        /*
         * File Types
         */
        public const val LF_NORMAL: Byte = '0'.code.toByte()
        public const val LF_LINK: Byte = '1'.code.toByte()
        public const val LF_SYMLINK: Byte = '2'.code.toByte()
        public const val LF_CHR: Byte = '3'.code.toByte()
        public const val LF_BLK: Byte = '4'.code.toByte()
        public const val LF_DIR: Byte = '5'.code.toByte()
        public const val LF_FIFO: Byte = '6'.code.toByte()
        public const val LF_CONTIG: Byte = '7'.code.toByte()

        /*
         * Ustar header
         */
        public const val USTAR_MAGIC: String = "ustar" // POSIX

        public const val USTAR_MAGICLEN: Int = 8
        public const val USTAR_USER_NAMELEN: Int = 32
        public const val USTAR_GROUP_NAMELEN: Int = 32
        public const val USTAR_DEVLEN: Int = 8
        public const val USTAR_FILENAME_PREFIX: Int = 155

        /**
         * Parses an entry name from a header buffer, reading up to [length] bytes from [offset]
         * or up to the first NUL, whichever comes first, and decoding them as UTF-8.
         *
         * @param header The header buffer to parse.
         * @param offset The offset into the buffer to parse from.
         * @param length The maximum number of bytes to parse.
         * @return The decoded entry name.
         */
        internal fun parseName(header: ByteArray, offset: Int, length: Int): String {
            val end = offset + length
            var nameEnd = offset

            while (nameEnd < end && header[nameEnd].toInt() != 0) {
                nameEnd++
            }

            return header.decodeToString(offset, nameEnd)
        }

        /**
         * Writes [name] into [buf] as UTF-8, NUL padded to exactly [length] bytes.
         *
         * A name whose UTF-8 encoding does not fit in [length] bytes is truncated on a character
         * boundary, so a multi-byte code point is never split across the end of the field.
         *
         * @param name The entry name to write.
         * @param buf The header buffer to write into.
         * @param offset The offset into the buffer to write at.
         * @param length The fixed width of the field, in bytes.
         * @return The offset just past the field that was written, i.e. `offset + length`.
         */
        internal fun getNameBytes(name: String, buf: ByteArray, offset: Int, length: Int): Int {
            var written = 0

            for (i in name.indices) {
                // A character at a time, so a multi-byte code point is dropped whole rather
                // than split across the end of the field.
                val charBytes = name[i].toString().encodeToByteArray()

                if (written + charBytes.size > length) break

                charBytes.copyInto(buf, offset + written)
                written += charBytes.size
            }

            buf.fill(0, offset + written, offset + length)

            return offset + length
        }

        /**
         * Creates a header for a file or directory entry.
         *
         * @param entryName The entry name. Backslashes are normalised to forward slashes and
         * leading and trailing slashes are removed. A name longer than [NAMELEN] is split across
         * [namePrefix] and [name]; one that cannot be split is truncated when written.
         * @param size The entry size in bytes. Ignored, and recorded as 0, when [dir] is true.
         * @param modTime Last modification time in **seconds** since the Unix epoch.
         * @param dir Whether the entry is a directory.
         * @param permissions The file mode (permission bits), as an octal value. See
         * [PermissionUtils.defaultOkioPermissions].
         */
        public fun createHeader(entryName: String, size: Long, modTime: Long, dir: Boolean, permissions: Int): TarHeader {
            // replace any non-standard file separators with forward slashes
            val name = entryName.replace('\\', '/').trim('/')

            val header = TarHeader()
            header.linkName = ""
            header.mode = permissions

            val lastSeparator = name.lastIndexOf('/')
            if (name.length > NAMELEN && lastSeparator != -1) {
                header.namePrefix = name.substring(0, lastSeparator)
                header.name = name.substring(lastSeparator + 1)
            } else {
                header.name = name
            }

            if (dir) {
                header.linkFlag = LF_DIR
                if (!header.name.endsWith('/')) {
                    header.name += "/"
                }
                header.size = 0
            } else {
                header.linkFlag = LF_NORMAL
                header.size = size
            }

            header.modTime = modTime
            header.checkSum = 0
            header.devMajor = 0
            header.devMinor = 0

            return header
        }
    }
}
