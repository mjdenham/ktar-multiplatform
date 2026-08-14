package org.martin.ktar

import okio.FileSystem
import okio.Path
import okio.SYSTEM
import org.martin.ktar.Octal.getCheckSumOctalBytes
import org.martin.ktar.Octal.getLongOctalBytes
import org.martin.ktar.Octal.getOctalBytes
import org.martin.ktar.Octal.parseOctal
import org.martin.ktar.PermissionUtils.defaultOkioPermissions
import org.martin.ktar.TarHeader.Companion.createHeader
import org.martin.ktar.TarHeader.Companion.getNameBytes
import org.martin.ktar.TarHeader.Companion.parseName

/**
 * A single entry in a tar archive: its [header], and the [file] it was created from, if any.
 */
public class TarEntry {
    /** The file this entry describes, or null if the entry was not created from one. */
    public val file: Path?

    /** The raw ustar header fields backing this entry. */
    public val header: TarHeader

    /**
     * Creates an entry describing [file], stored in the archive as [entryName].
     *
     * The file's size, modification time and directory flag are read from the file system.
     */
    public constructor(file: Path, entryName: String) {
        this.file = file
        this.header = extractTarHeader(file, entryName)
    }

    /**
     * Creates an entry by parsing a [TarConstants.HEADER_BLOCK] byte header block read from
     * an archive.
     */
    public constructor(headerBuf: ByteArray) {
        this.file = null
        this.header = TarHeader()
        parseTarHeader(headerBuf)
    }

    /**
     * Creates an entry from an existing [TarHeader].
     *
     * This is useful to add new entries programmatically (e.g. for adding files or directories
     * that do not exist in the file system).
     */
    public constructor(header: TarHeader) {
        this.file = null
        this.header = header
    }

    override fun equals(other: Any?): Boolean {
        if (other !is TarEntry) {
            return false
        }
        return header.name == other.header.name
    }

    override fun hashCode(): Int {
        return header.name.hashCode()
    }

    public fun isDescendent(desc: TarEntry): Boolean {
        return desc.header.name.startsWith(header.name)
    }

    /**
     * The full entry name, including the ustar filename prefix if the header carries one.
     *
     * Note that setting this does not split a long name back out into the prefix field, so
     * setting the value returned by the getter is not necessarily a no-op for long names.
     */
    public var name: String
        get() {
            return if (header.namePrefix.isEmpty()) {
                header.name
            } else {
                header.namePrefix + "/" + header.name
            }
        }
        set(name) {
            header.name = name
        }

    public var userId: Int
        get() = header.userId
        set(userId) {
            header.userId = userId
        }

    public var groupId: Int
        get() = header.groupId
        set(groupId) {
            header.groupId = groupId
        }

    public var userName: String
        get() = header.userName
        set(userName) {
            header.userName = userName
        }

    public var groupName: String
        get() = header.groupName
        set(groupName) {
            header.groupName = groupName
        }

    public fun setIds(userId: Int, groupId: Int) {
        this.userId = userId
        this.groupId = groupId
    }

    /**
     * Sets the last modification time from a value in **milliseconds** since the Unix epoch.
     *
     * The header itself stores seconds ([TarHeader.modTime]), so the value is converted here.
     */
    public fun setModTimeMillis(timeMillis: Long) {
        header.modTime = timeMillis / 1000
    }

    public var size: Long
        get() = header.size
        set(size) {
            header.size = size
        }

    /**
     * Whether this entry is a directory.
     *
     * For an entry created from a [file] this reflects the file system; otherwise it is taken
     * from the header's link flag or a trailing slash on the name.
     */
    public val isDirectory: Boolean
        get() {
            file?.let { file ->
                return FileSystem.SYSTEM.metadata(file).isDirectory
            }

            if (header.linkFlag == TarHeader.LF_DIR) return true

            if (header.name.endsWith('/')) return true

            return false
        }

    /**
     * Calculate checksum
     */
    internal fun computeCheckSum(buf: ByteArray): Long {
        return buf.sumOf { (it.toInt() and 0xFF).toLong() }
    }

    /**
     * Writes the header to the byte buffer
     */
    internal fun writeEntryHeader(outbuf: ByteArray) {
        var offset = 0

        offset = getNameBytes(header.name, outbuf, offset, TarHeader.NAMELEN)
        offset = getOctalBytes(header.mode.toLong(), outbuf, offset, TarHeader.MODELEN)
        offset = getOctalBytes(header.userId.toLong(), outbuf, offset, TarHeader.UIDLEN)
        offset = getOctalBytes(header.groupId.toLong(), outbuf, offset, TarHeader.GIDLEN)

        val size = header.size

        offset = getLongOctalBytes(size, outbuf, offset, TarHeader.SIZELEN)
        offset = getLongOctalBytes(header.modTime, outbuf, offset, TarHeader.MODTIMELEN)

        val csOffset = offset
        for (c in 0..<TarHeader.CHKSUMLEN) outbuf[offset++] = ' '.code.toByte()

        outbuf[offset++] = header.linkFlag

        offset = getNameBytes(header.linkName, outbuf, offset, TarHeader.NAMELEN)
        offset = getNameBytes(header.magic, outbuf, offset, TarHeader.USTAR_MAGICLEN)
        offset = getNameBytes(header.userName, outbuf, offset, TarHeader.USTAR_USER_NAMELEN)
        offset = getNameBytes(header.groupName, outbuf, offset, TarHeader.USTAR_GROUP_NAMELEN)
        offset = getOctalBytes(header.devMajor.toLong(), outbuf, offset, TarHeader.USTAR_DEVLEN)
        offset = getOctalBytes(header.devMinor.toLong(), outbuf, offset, TarHeader.USTAR_DEVLEN)
        offset = getNameBytes(header.namePrefix, outbuf, offset, TarHeader.USTAR_FILENAME_PREFIX)

        while (offset < outbuf.size) {
            outbuf[offset++] = 0
        }

        val checkSum = this.computeCheckSum(outbuf)

        getCheckSumOctalBytes(checkSum, outbuf, csOffset, TarHeader.CHKSUMLEN)
    }

    /**
     * Parses the tar header to the byte buffer
     */
    internal fun parseTarHeader(bh: ByteArray) {
        var offset = 0

        header.name = parseName(bh, offset, TarHeader.NAMELEN)
        offset += TarHeader.NAMELEN

        header.mode = parseOctal(bh, offset, TarHeader.MODELEN).toInt()
        offset += TarHeader.MODELEN

        header.userId = parseOctal(bh, offset, TarHeader.UIDLEN).toInt()
        offset += TarHeader.UIDLEN

        header.groupId = parseOctal(bh, offset, TarHeader.GIDLEN).toInt()
        offset += TarHeader.GIDLEN

        header.size = parseOctal(bh, offset, TarHeader.SIZELEN)
        offset += TarHeader.SIZELEN

        header.modTime = parseOctal(bh, offset, TarHeader.MODTIMELEN)
        offset += TarHeader.MODTIMELEN

        header.checkSum = parseOctal(bh, offset, TarHeader.CHKSUMLEN).toInt()
        offset += TarHeader.CHKSUMLEN

        header.linkFlag = bh[offset++]

        header.linkName = parseName(bh, offset, TarHeader.NAMELEN)
        offset += TarHeader.NAMELEN

        header.magic = parseName(bh, offset, TarHeader.USTAR_MAGICLEN)
        offset += TarHeader.USTAR_MAGICLEN

        header.userName = parseName(bh, offset, TarHeader.USTAR_USER_NAMELEN)
        offset += TarHeader.USTAR_USER_NAMELEN

        header.groupName = parseName(bh, offset, TarHeader.USTAR_GROUP_NAMELEN)
        offset += TarHeader.USTAR_GROUP_NAMELEN

        header.devMajor = parseOctal(bh, offset, TarHeader.USTAR_DEVLEN).toInt()
        offset += TarHeader.USTAR_DEVLEN

        header.devMinor = parseOctal(bh, offset, TarHeader.USTAR_DEVLEN).toInt()
        offset += TarHeader.USTAR_DEVLEN

        header.namePrefix = parseName(bh, offset, TarHeader.USTAR_FILENAME_PREFIX)
    }

    private companion object {
        /**
         * Builds a header from a file's metadata.
         *
         * okio has no permissions API, so a fixed read-access mode is used.
         */
        fun extractTarHeader(file: Path, entryName: String): TarHeader {
            val metadata = FileSystem.SYSTEM.metadata(file)
            return createHeader(
                entryName,
                metadata.size ?: 0,
                metadata.lastModifiedAtMillis?.div(1000) ?: 0,
                metadata.isDirectory,
                defaultOkioPermissions(),
            )
        }
    }
}
