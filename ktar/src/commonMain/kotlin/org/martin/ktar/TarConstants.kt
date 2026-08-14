package org.martin.ktar

/**
 * The fixed block sizes of the tar format, in bytes.
 */
public object TarConstants {
    /** The two all-zero blocks that mark the end of an archive. */
    public const val EOF_BLOCK: Int = 1024

    /** The block size that entry content is padded up to. */
    public const val DATA_BLOCK: Int = 512

    /** The size of an entry's header block. */
    public const val HEADER_BLOCK: Int = 512
}
