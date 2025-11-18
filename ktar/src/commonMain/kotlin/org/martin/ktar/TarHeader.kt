package org.martin.ktar

import org.martin.ktar.TarUtils.trim
import kotlin.collections.filter

/**
 * Header
 *
 * <pre>
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
</pre> *
 *
 *
 * File Types
 *
 * <pre>
 * Value        Meaning
 * '0'          Normal file
 * (ASCII NUL)  Normal file (now obsolete)
 * '1'          Hard link
 * '2'          Symbolic link
 * '3'          Character special
 * '4'          Block special
 * '5'          Directory
 * '6'          FIFO
 * '7'          Contigous
</pre> *
 *
 *
 *
 * Ustar header
 *
 * <pre>
 * Offset  Size    Field
 * 257     6       UStar indicator "ustar"
 * 263     2       UStar version "00"
 * 265     32      Owner user name
 * 297     32      Owner group name
 * 329     8       Device major number
 * 337     8       Device minor number
 * 345     155     Filename prefix
</pre> *
 */
class TarHeader {
    // Header values
	var name: StringBuilder
	var mode: Int = 0
	var userId: Int
	var groupId: Int
	var size: Long = 0
	var modTime: Long = 0
	var checkSum: Int = 0
	var linkFlag: Byte = 0
	var linkName: StringBuilder
	var magic: StringBuilder // ustar indicator and version
	var userName: StringBuilder
	var groupName: StringBuilder
	var devMajor: Int = 0
	var devMinor: Int = 0
	var namePrefix: StringBuilder

    init {
        this.magic = StringBuilder(USTAR_MAGIC)

        this.name = StringBuilder()
        this.linkName = StringBuilder()

        var user = "" // System.getProperty("user.name", "")

        if (user!!.length > 31) user = user.substring(0, 31)

        this.userId = 0
        this.groupId = 0
        this.userName = StringBuilder(user)
        this.groupName = StringBuilder("")
        this.namePrefix = StringBuilder()
    }

    companion object {
        /*
	 * Header
	 */
        const val NAMELEN: Int = 100
        const val MODELEN: Int = 8
        const val UIDLEN: Int = 8
        const val GIDLEN: Int = 8
        const val SIZELEN: Int = 12
        const val MODTIMELEN: Int = 12
        const val CHKSUMLEN: Int = 8
        const val LF_OLDNORM: Byte = 0

        /*
	 * File Types
	 */
        const val LF_NORMAL: Byte = '0'.code.toByte()
        const val LF_LINK: Byte = '1'.code.toByte()
        const val LF_SYMLINK: Byte = '2'.code.toByte()
        const val LF_CHR: Byte = '3'.code.toByte()
        const val LF_BLK: Byte = '4'.code.toByte()
        const val LF_DIR: Byte = '5'.code.toByte()
        const val LF_FIFO: Byte = '6'.code.toByte()
        const val LF_CONTIG: Byte = '7'.code.toByte()

        /*
	 * Ustar header
	 */
        const val USTAR_MAGIC: String = "ustar" // POSIX

        const val USTAR_MAGICLEN: Int = 8
        const val USTAR_USER_NAMELEN: Int = 32
        const val USTAR_GROUP_NAMELEN: Int = 32
        const val USTAR_DEVLEN: Int = 8
        const val USTAR_FILENAME_PREFIX: Int = 155

        /**
         * Parse an entry name from a header buffer.
         *
         * @param header
         * The header buffer from which to parse.
         * @param offset
         * The offset into the buffer from which to parse.
         * @param length
         * The number of header bytes to parse.
         * @return The header's entry name.
         */
		fun parseName(header: ByteArray, offset: Int, length: Int): StringBuilder {
            val end = offset + length
            val buffer = header
                .copyOfRange(offset, end)
                .filter { it != 0.toByte() }
                .toByteArray()
            val result = StringBuilder(buffer.decodeToString())

            return result
        }

        /**
         * Determine the number of bytes in an entry name.
         *
         * @param name
         * The header buffer from which to parse.
         * @param offset
         * The offset into the buffer from which to parse.
         * @param length
         * The number of header bytes to parse.
         * @return The number of bytes in a header's entry name.
         */
		fun getNameBytes(name: StringBuilder, buf: ByteArray, offset: Int, length: Int): Int {
            var i = 0
            while (i < length && i < name.length) {
                buf[offset + i] = name[i].code.toByte()
                ++i
            }

            while (i < length) {
                buf[offset + i] = 0
                ++i
            }

            return offset + length
        }

        /**
         * Creates a new header for a file/directory entry.
         *
         *
         * @param entryName
         * File name
         * @param size
         * File size in bytes
         * @param modTime
         * Last modification time in numeric Unix time format
         * @param dir
         * Is directory
         */
		fun createHeader(entryName: String, size: Long, modTime: Long, dir: Boolean, permissions: Int): TarHeader {
            var name = entryName
            // replace any non-standard file separators with forward slashes
            name = trim(name.replace('\\', '/'), '/')

            val header = TarHeader()
            header.linkName = StringBuilder("")
            header.mode = permissions

            if (name.length > 100) {
                header.namePrefix = StringBuilder(name.substring(0, name.lastIndexOf('/')))
                header.name = StringBuilder(name.substring(name.lastIndexOf('/') + 1))
            } else {
                header.name = StringBuilder(name)
            }
            if (dir) {
                header.linkFlag = LF_DIR
                if (header.name[header.name.length - 1] != '/') {
                    header.name.append("/")
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