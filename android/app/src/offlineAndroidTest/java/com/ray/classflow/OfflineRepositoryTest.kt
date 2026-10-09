package com.ray.classflow

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.ray.classflow.data.ClassFlowRepository
import com.ray.classflow.data.db.ClassFlowDatabase
import com.ray.classflow.model.*
import com.ray.classflow.sync.CredentialStore
import com.ray.classflow.sync.NextcloudApi
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OfflineRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installedOfflineAppHasNoNetworkPermissionsOrCloudBackup() {
        assertFalse(BuildConfig.CLOUD_SYNC_ENABLED)
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.INTERNET),
        )
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE),
        )
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val simplified = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        }
        val traditional = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.TRADITIONAL_CHINESE)
        }
        assertEquals("ClassFlow 离线版", context.createConfigurationContext(simplified).getString(R.string.app_name))
        assertEquals("ClassFlow 離線版", context.createConfigurationContext(traditional).getString(R.string.app_name))
    }

    @Test
    fun coreEditsRemainLocalWithoutQueueOrSyncWorkers() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ClassFlowDatabase::class.java).build()
        val repo = ClassFlowRepository(context, database, CredentialStore(context), NextcloudApi())
        val dao = database.dao()
        val course = Course(UUID.randomUUID().toString(), "測試課程")
        val slot = TimetableSlot(UUID.randomUUID().toString(), course.id, 1, 480, 540)
        val agenda =
            AgendaItem(
                UUID.randomUUID().toString(),
                AgendaType.EXAM,
                "測試考試",
                2000,
                linkedSlotIds = listOf(slot.id),
            )
        val plan =
            StudyPlan(UUID.randomUUID().toString(), "測試計劃", 3000, 4000, linkedAgendaId = agenda.id)
        try {
            repo.saveCourse(course)
            repo.saveSlot(slot)
            repo.saveAgenda(agenda)
            repo.saveStudyPlan(plan)
            assertEquals("LOCAL", dao.allCourses().single().syncState)
            assertEquals("LOCAL", dao.allSlots().single().syncState)
            assertEquals("LOCAL", dao.allAgenda().single().agenda.syncState)
            assertEquals("LOCAL", dao.allStudyPlans().single().syncState)
            repo.setAgendaCompleted(agenda, true)
            assertEquals("COMPLETED", dao.allAgenda().single().agenda.status)
            repo.scheduleImmediateSync()
            repo.schedulePeriodicSync()
            assertTrue(repo.performSync())
            assertNull(repo.account())
            assertNull(repo.pendingLogin())
            assertTrue(dao.pendingMutations().isEmpty())
            val workers = WorkManager.getInstance(context)
            assertTrue(
                workers.getWorkInfosForUniqueWork("classflow-immediate-sync").get().isEmpty()
            )
            assertTrue(workers.getWorkInfosForUniqueWork("classflow-periodic-sync").get().isEmpty())
            repo.deleteStudyPlan(plan)
            repo.deleteAgenda(agenda)
            repo.deleteSlot(slot)
            repo.deleteCourse(course)
            assertTrue(dao.allStudyPlans().isEmpty())
            assertTrue(dao.allAgenda().isEmpty())
            assertTrue(dao.allSlots().isEmpty())
            assertTrue(dao.allCourses().isEmpty())
            assertTrue(dao.pendingMutations().isEmpty())
        } finally {
            database.close()
        }
    }
}
