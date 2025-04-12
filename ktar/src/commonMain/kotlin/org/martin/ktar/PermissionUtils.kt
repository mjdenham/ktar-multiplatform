package org.martin.ktar

/**
 * Helps dealing with file permissions.
 * Unfortunately okio does not have a permissions api so we default to Read permission.
 */
object PermissionUtils {

    /**
     * okio has no permissions api so just assume READ access by default
     */
    fun defaultOkioPermissions() = StandardFilePermission.READ.mode

    /**
     * XXX: When using standard Java permissions, we treat 'owner' and 'group' equally and give no
     * permissions for 'others'.
     */
    private enum class StandardFilePermission(val mode: Int) {
        EXECUTE(72), WRITE(144), READ(288)
    }
}
