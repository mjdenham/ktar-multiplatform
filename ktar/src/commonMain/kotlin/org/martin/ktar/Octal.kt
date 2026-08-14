package org.martin.ktar

import okio.IOException

internal object Octal {
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
     * @return The offset just past the field that was written, i.e. `offset + length`.
     * @throws IOException if [value] is negative, or too large for a field of this width. A
     * value that does not fit would otherwise be written as its truncated low order digits,
     * silently producing a corrupt header.
     */
    fun getOctalBytes(value: Long, buf: ByteArray, offset: Int, length: Int): Int {
        if (value < 0) {
            throw IOException("Cannot write negative value $value to a tar header field")
        }

        var idx = length - 1

        buf[offset + idx] = 0
        --idx
        buf[offset + idx] = ' '.code.toByte()
        --idx

        if (value == 0L) {
            buf[offset + idx] = '0'.code.toByte()
            --idx
        } else {
            var remaining = value
            while (idx >= 0 && remaining > 0) {
                buf[offset + idx] = ('0'.code.toByte() + (remaining and 7L).toByte()).toByte()
                remaining = remaining shr 3
                --idx
            }

            if (remaining > 0) {
                throw IOException(
                    "Value $value is too large for a $length byte tar header field " +
                        "(maximum ${maxValueFor(length)})"
                )
            }
        }

        while (idx >= 0) {
            buf[offset + idx] = '0'.code.toByte()
            --idx
        }

        return offset + length
    }

    /**
     * The largest value a field of [length] bytes can hold. Two of the bytes are taken by the
     * trailing space and NUL, leaving three bits of value per remaining byte.
     */
    private fun maxValueFor(length: Int): Long {
        val digits = length - 2
        return if (digits >= 21) Long.MAX_VALUE else (1L shl (digits * 3)) - 1
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
     * @return The offset just past the field that was written, i.e. `offset + length`.
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
     * @return The offset just past the field that was written, i.e. `offset + length`.
     */
    fun getLongOctalBytes(value: Long, buf: ByteArray, offset: Int, length: Int): Int {
        val temp = ByteArray(length + 1)
        getOctalBytes(value, temp, 0, length + 1)
        temp.copyInto(buf, offset, 0, length)

        return offset + length
    }
}
