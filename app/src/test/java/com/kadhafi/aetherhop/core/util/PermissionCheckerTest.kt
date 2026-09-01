package com.kadhafi.aetherhop.core.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCheckerTest {

    @Test
    fun testGetRequiredPermissionsReturnsNonEmptyArray() {
        val permissions = PermissionChecker.getRequiredPermissions()
        assertNotNull(permissions)
        assertTrue(permissions.isNotEmpty())
    }
}
