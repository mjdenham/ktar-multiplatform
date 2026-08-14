package org.martin.ktar

/**
 * Helps dealing with file permissions.
 *
 * okio has no permissions API, so entries created from a [okio.Path] cannot carry the real mode
 * of the file they describe and are given read access instead. Pass an explicit mode to
 * [TarHeader.createHeader] if you need something else.
 */
public object PermissionUtils {

    /**
     * The mode used for entries created from a file: read access for owner and group, and
     * nothing for others (octal 0440).
     */
    public fun defaultOkioPermissions(): Int = READ_MODE

    /** Read permission for owner and group, with none for others — octal 0440. */
    private const val READ_MODE = 288
}
