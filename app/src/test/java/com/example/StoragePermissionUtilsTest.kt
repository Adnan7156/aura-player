package com.example

import com.example.util.StoragePermissionUtils
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePermissionUtilsTest {

    @Test
    fun getRequiredStoragePermissions_returnsNonEmptyList() {
        val permissions = StoragePermissionUtils.getRequiredStoragePermissions()
        assertNotNull(permissions)
        assertTrue(permissions.isNotEmpty())
    }

    @Test
    fun getAudioStoragePermissions_returnsNonEmptyList() {
        val audioPermissions = StoragePermissionUtils.getAudioStoragePermissions()
        assertNotNull(audioPermissions)
        assertTrue(audioPermissions.isNotEmpty())
    }

    @Test
    fun getVideoStoragePermissions_returnsNonEmptyList() {
        val videoPermissions = StoragePermissionUtils.getVideoStoragePermissions()
        assertNotNull(videoPermissions)
        assertTrue(videoPermissions.isNotEmpty())
    }
}
