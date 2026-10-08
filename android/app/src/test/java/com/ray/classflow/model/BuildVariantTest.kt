package com.ray.classflow.model

import com.ray.classflow.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class BuildVariantTest {
    @Test
    fun flavorSeparatesAppIdentityAndCloudAccess() {
        val offline = BuildConfig.FLAVOR == "offline"
        assertEquals(!offline, BuildConfig.CLOUD_SYNC_ENABLED)
        val expected =
            "com.ray.classflow" +
                (if (offline) ".offline" else "") +
                (if (BuildConfig.DEBUG) ".debug" else "")
        assertEquals(expected, BuildConfig.APPLICATION_ID)
    }

    @Test
    fun offlineWritesAreLocalRatherThanWaitingForSync() {
        assertEquals(SyncState.LOCAL, SyncState.forLocalWrite(false))
        assertNotEquals(SyncState.PENDING, SyncState.forLocalWrite(false))
    }

    @Test
    fun cloudWritesStillEnterTheOriginalPendingState() {
        assertEquals(SyncState.PENDING, SyncState.forLocalWrite(true))
    }
}
