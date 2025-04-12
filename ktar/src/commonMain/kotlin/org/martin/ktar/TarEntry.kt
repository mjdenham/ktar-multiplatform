
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

class TarEntry {
    var file: Path?
        protected set
    var header: TarHeader?
        protected set

    private constructor() {
        this.file = null
        header = TarHeader()
    }

    constructor(file: Path, entryName: String) : this() {
        this.file = file
        this.extractTarHeader(entryName)
    }

    constructor(headerBuf: ByteArray) : this() {
        this.parseTarHeader(headerBuf)
    }

    /**
     * Constructor to create an entry from an existing TarHeader object.
     *
     * This method is useful to add new entries programmatically (e.g. for
     * adding files or directories that do not exist in the file system).
     */
    constructor(header: TarHeader?) {
        this.file = null
        this.header = header
    }

    override fun equals(it: Any?): Boolean {
        if (it !is TarEntry) {
            return false
        }
        return header!!.name.toString() == it.header!!.name.toString()
    }

    override fun hashCode(): Int {
        return header!!.name.hashCode()
    }

    fun isDescendent(desc: TarEntry): Boolean {
        return desc.header!!.name.toString().startsWith(header!!.name.toString())
    }

    var name: String
        get() {
            var name = header!!.name.toString()
            if (header!!.namePrefix != null && header!!.namePrefix.toString() != "") {
                name = header!!.namePrefix.toString() + "/" + name
            }

            return name
        }
        set(name) {
            header!!.name = StringBuilder(name)
        }

    var userId: Int
        get() = header!!.userId
        set(userId) {
            header!!.userId = userId
        }

    var groupId: Int
        get() = header!!.groupId
        set(groupId) {
            header!!.groupId = groupId
        }

    var userName: String
        get() = header!!.userName.toString()
        set(userName) {
            header!!.userName = StringBuilder(userName)
        }

    var groupName: String
        get() = header!!.groupName.toString()
        set(groupName) {
            header!!.groupName = StringBuilder(groupName)
        }

    fun setIds(userId: Int, groupId: Int) {
        this.userId = userId
        this.groupId = groupId
    }

    fun setModTime(time: Long) {
        header!!.modTime = time / 1000
    }

    var size: Long
        get() = header!!.size
        set(size) {
            header!!.size = size
        }

    val isDirectory: Boolean
        get() {
            file?.let { file ->
                return FileSystem.SYSTEM.metadata(file).isDirectory
            }

            header?.let { header ->
                if (header.linkFlag == TarHeader.LF_DIR) return true

                if (header.name.toString().endsWith("/")) return true
            }

            return false
        }

    /**
     * Extract header from File
     */
    fun extractTarHeader(entryName: String) {
        file?.let { file ->
            val metadata = FileSystem.SYSTEM.metadata(file)
            val permissions = defaultOkioPermissions() // okio has no permissions api so just assume READ access by default //permissions(metadata)
            header = createHeader(entryName, metadata.size ?: 0, metadata.lastModifiedAtMillis?.div(1000) ?: 0, metadata.isDirectory, permissions)
        } ?: throw Exception("File is null")
    }

    /**
     * Calculate checksum
     */
    fun computeCheckSum(buf: ByteArray): Long {
        var sum: Long = 0

        for (i in buf.indices) {
            sum += (255 and buf[i].toInt()).toLong()
        }

        return sum
    }

    /**
     * Writes the header to the byte buffer
     */
    fun writeEntryHeader(outbuf: ByteArray) {
        var offset = 0

        offset = getNameBytes(header!!.name, outbuf, offset, TarHeader.NAMELEN)
        offset = getOctalBytes(header!!.mode.toLong(), outbuf, offset, TarHeader.MODELEN)
        offset = getOctalBytes(header!!.userId.toLong(), outbuf, offset, TarHeader.UIDLEN)
        offset = getOctalBytes(header!!.groupId.toLong(), outbuf, offset, TarHeader.GIDLEN)

        val size = header!!.size

        offset = getLongOctalBytes(size, outbuf, offset, TarHeader.SIZELEN)
        offset = getLongOctalBytes(header!!.modTime, outbuf, offset, TarHeader.MODTIMELEN)

        val csOffset = offset
        for (c in 0..<TarHeader.CHKSUMLEN) outbuf[offset++] = ' '.code.toByte()

        outbuf[offset++] = header!!.linkFlag

        offset = getNameBytes(header!!.linkName, outbuf, offset, TarHeader.NAMELEN)
        offset = getNameBytes(header!!.magic, outbuf, offset, TarHeader.USTAR_MAGICLEN)
        offset = getNameBytes(header!!.userName, outbuf, offset, TarHeader.USTAR_USER_NAMELEN)
        offset = getNameBytes(header!!.groupName, outbuf, offset, TarHeader.USTAR_GROUP_NAMELEN)
        offset = getOctalBytes(header!!.devMajor.toLong(), outbuf, offset, TarHeader.USTAR_DEVLEN)
        offset = getOctalBytes(header!!.devMinor.toLong(), outbuf, offset, TarHeader.USTAR_DEVLEN)
        offset = getNameBytes(header!!.namePrefix, outbuf, offset, TarHeader.USTAR_FILENAME_PREFIX)

        while (offset < outbuf.size) {
            outbuf[offset++] = 0
        }

        val checkSum = this.computeCheckSum(outbuf)

        getCheckSumOctalBytes(checkSum, outbuf, csOffset, TarHeader.CHKSUMLEN)
    }

    /**
     * Parses the tar header to the byte buffer
     */
    fun parseTarHeader(bh: ByteArray) {
        var offset = 0

        header!!.name = parseName(bh, offset, TarHeader.NAMELEN)
        offset += TarHeader.NAMELEN

        header!!.mode = parseOctal(bh, offset, TarHeader.MODELEN).toInt()
        offset += TarHeader.MODELEN

        header!!.userId = parseOctal(bh, offset, TarHeader.UIDLEN).toInt()
        offset += TarHeader.UIDLEN

        header!!.groupId = parseOctal(bh, offset, TarHeader.GIDLEN).toInt()
        offset += TarHeader.GIDLEN

        header!!.size = parseOctal(bh, offset, TarHeader.SIZELEN)
        offset += TarHeader.SIZELEN

        header!!.modTime = parseOctal(bh, offset, TarHeader.MODTIMELEN)
        offset += TarHeader.MODTIMELEN

        header!!.checkSum = parseOctal(bh, offset, TarHeader.CHKSUMLEN).toInt()
        offset += TarHeader.CHKSUMLEN

        header!!.linkFlag = bh[offset++]

        header!!.linkName = parseName(bh, offset, TarHeader.NAMELEN)
        offset += TarHeader.NAMELEN

        header!!.magic = parseName(bh, offset, TarHeader.USTAR_MAGICLEN)
        offset += TarHeader.USTAR_MAGICLEN

        header!!.userName = parseName(bh, offset, TarHeader.USTAR_USER_NAMELEN)
        offset += TarHeader.USTAR_USER_NAMELEN

        header!!.groupName = parseName(bh, offset, TarHeader.USTAR_GROUP_NAMELEN)
        offset += TarHeader.USTAR_GROUP_NAMELEN

        header!!.devMajor = parseOctal(bh, offset, TarHeader.USTAR_DEVLEN).toInt()
        offset += TarHeader.USTAR_DEVLEN

        header!!.devMinor = parseOctal(bh, offset, TarHeader.USTAR_DEVLEN).toInt()
        offset += TarHeader.USTAR_DEVLEN

        header!!.namePrefix = parseName(bh, offset, TarHeader.USTAR_FILENAME_PREFIX)
    }
}