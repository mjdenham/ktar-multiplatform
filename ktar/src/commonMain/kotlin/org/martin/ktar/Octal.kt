package org.martin.ktar

object Octal {
    /**
     * Parse an octal string from a header buffer. This is used for the file
     * permission mode value.
     *
     * @param header
     * The header buffer from which to parse.
     * @param offset
     * The offset into the buffer from which to parse.
     * @param length
     * The number of header bytes to parse.
     *
     * @return The long value of the octal string.
     */
    fun parseOctal(header: ByteArray, offset: Int, length: Int): Long {
        var result: Long = 0
        var stillPadding = true

        val end = offset + length
        for (i in offset..<end) {
            if (header[i].toInt() == 0) break

            if (header[i] == ' '.code.toByte() || header[i] == '0'.code.toByte()) {
                if (stillPadding) continue

                if (header[i] == ' '.code.toByte()) break
            }

            stillPadding = false

            result = (result shl 3) + (header[i] - '0'.code.toByte())
        }

        return result
    }

    /**
     * Write an octal integer to a header buffer.
     *
     * @param value
     * The value to write.
     * @param buf
     * The header buffer from which to parse.
     * @param offset
     * The offset into the buffer from which to parse.
     * @param length
     * The number of header bytes to parse.
     *
     * @return The integer value of the octal bytes.
     */
    fun getOctalBytes(value: Long, buf: ByteArray, offset: Int, length: Int): Int {
        var idx = length - 1

        buf[offset + idx] = 0
        --idx
        buf[offset + idx] = ' '.code.toByte()
        --idx

        if (value == 0L) {
            buf[offset + idx] = '0'.code.toByte()
            --idx
        } else {
            var `val` = value
            while (idx >= 0 && `val` > 0) {
                buf[offset + idx] = ('0'.code.toByte() + (`val` and 7L).toByte()).toByte()
                `val` = `val` shr 3
                --idx
            }
        }

        while (idx >= 0) {
            buf[offset + idx] = '0'.code.toByte()
            --idx
        }

        return offset + length
    }

    /**
     * Write the checksum octal integer to a header buffer.
     *
     * @param value
     * The value to write.
     * @param buf
     * The header buffer from which to parse.
     * @param offset
     * The offset into the buffer from which to parse.
     * @param length
     * The number of header bytes to parse.
     * @return The integer value of the entry's checksum.
     */
    fun getCheckSumOctalBytes(value: Long, buf: ByteArray, offset: Int, length: Int): Int {
        getOctalBytes(value, buf, offset, length)
        buf[offset + length - 1] = ' '.code.toByte()
        buf[offset + length - 2] = 0
        return offset + length
    }

    /**
     * Write an octal long integer to a header buffer.
     *
     * @param value
     * The value to write.
     * @param buf
     * The header buffer from which to parse.
     * @param offset
     * The offset into the buffer from which to parse.
     * @param length
     * The number of header bytes to parse.
     *
     * @return The long value of the octal bytes.
     */
    fun getLongOctalBytes(value: Long, buf: ByteArray, offset: Int, length: Int): Int {
        val temp = ByteArray(length + 1)
        getOctalBytes(value, temp, 0, length + 1)
        temp.copyInto(buf, offset, 0, length)

        return offset + length
    }
}
