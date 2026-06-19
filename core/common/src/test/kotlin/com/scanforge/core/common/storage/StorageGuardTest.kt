package com.scanforge.core.common.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageGuardTest {

    private val mb = 1024L * 1024

    @Test
    fun `allows write with ample free space`() {
        assertTrue(StorageGuard.hasHeadroom(freeBytes = 500 * mb, requiredBytes = 10 * mb))
        assertEquals(StorageStatus.Ok, StorageGuard.classify(500 * mb, 10 * mb))
    }

    @Test
    fun `blocks write that would not fit`() {
        assertFalse(StorageGuard.hasHeadroom(freeBytes = 5 * mb, requiredBytes = 10 * mb))
        assertEquals(StorageStatus.Insufficient, StorageGuard.classify(5 * mb, 10 * mb))
    }

    @Test
    fun `write that fits but eats the safety margin is Tight`() {
        // 60 MB free, need 30 MB -> 30 MB left, below the 50 MB margin but the file still fits.
        assertFalse(StorageGuard.hasHeadroom(freeBytes = 60 * mb, requiredBytes = 30 * mb))
        assertEquals(StorageStatus.Tight, StorageGuard.classify(60 * mb, 30 * mb))
    }

    @Test
    fun `zero requirement is always allowed`() {
        assertTrue(StorageGuard.hasHeadroom(freeBytes = 0, requiredBytes = 0))
        assertTrue(StorageGuard.hasHeadroom(freeBytes = 1, requiredBytes = -5))
    }

    @Test
    fun `unknown free space fails closed`() {
        assertFalse(StorageGuard.hasHeadroom(freeBytes = -1, requiredBytes = 1 * mb))
        assertEquals(StorageStatus.Insufficient, StorageGuard.classify(-1, 1 * mb))
    }

    @Test
    fun `custom safety margin is honoured`() {
        assertTrue(StorageGuard.hasHeadroom(60 * mb, 30 * mb, safetyMarginBytes = 10 * mb))
        assertFalse(StorageGuard.hasHeadroom(60 * mb, 30 * mb, safetyMarginBytes = 40 * mb))
    }
}
