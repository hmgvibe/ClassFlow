package com.ray.classflow.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ray.classflow.data.db.ClassFlowDatabase
import com.ray.classflow.data.db.StudyPlanEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlanMigrationTest {
    @Test
    fun upgradePreservesCoursesAndPendingEditsAndStoresPlans() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "study-plan-migration-test.db"
        context.deleteDatabase(name)
        val schema =
            instrumentation.context.assets
                .open("com.ray.classflow.data.db.ClassFlowDatabase/1.json")
                .bufferedReader()
                .use { JSONObject(it.readText()).getJSONObject("database") }
        val helper =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(name)
                        .callback(
                            object : SupportSQLiteOpenHelper.Callback(1) {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    val entities = schema.getJSONArray("entities")
                                    for (index in 0 until entities.length()) {
                                        val entity = entities.getJSONObject(index)
                                        val table = entity.getString("tableName")
                                        db.execSQL(
                                            entity
                                                .getString("createSql")
                                                .replace("\${TABLE_NAME}", table)
                                        )
                                        val indices = entity.optJSONArray("indices")
                                        for (i in 0 until (indices?.length() ?: 0)) db.execSQL(
                                            indices
                                                ?.getJSONObject(i)!!
                                                .getString("createSql")
                                                .replace("\${TABLE_NAME}", table)
                                        )
                                    }
                                    val setup = schema.getJSONArray("setupQueries")
                                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                                    db.execSQL(
                                        "INSERT INTO courses VALUES ('course', '既有課程', '', '', 0, '', 1, 1, 'SYNCED')"
                                    )
                                    db.execSQL(
                                        "INSERT INTO pending_mutations (operationId, entityType, entityId, operation, baseVersion, payload, createdAt) VALUES ('pending', 'course', 'course', 'delete', 1, NULL, 1)"
                                    )
                                }

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = Unit
                            }
                        )
                        .build()
                )
        helper.writableDatabase
        helper.close()
        val migrated =
            Room.databaseBuilder(context, ClassFlowDatabase::class.java, name)
                .addMigrations(ClassFlowDatabase.MIGRATION_1_2)
                .build()
        try {
            assertEquals("既有課程", migrated.dao().allCourses().single().name)
            assertEquals("pending", migrated.dao().pendingMutations().single().operationId)
            val plan =
                StudyPlanEntity("plan", "學習", 1000, 2000, "course", null, "", 0, 1000, "PENDING")
            migrated.dao().upsertStudyPlan(plan)
            assertEquals(plan, migrated.dao().allStudyPlans().single())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
