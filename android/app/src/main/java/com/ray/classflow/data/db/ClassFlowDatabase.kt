package com.ray.classflow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities =
        [
            CourseEntity::class,
            TimetableSlotEntity::class,
            AgendaEntity::class,
            AgendaLinkEntity::class,
            PendingMutationEntity::class,
            StudyPlanEntity::class,
        ],
    version = 2,
    exportSchema = true,
)
abstract class ClassFlowDatabase : RoomDatabase() {
    abstract fun dao(): ClassFlowDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE IF NOT EXISTS study_plans (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL,
                    startsAt INTEGER NOT NULL, endsAt INTEGER NOT NULL,
                    linkedCourseId TEXT, linkedAgendaId TEXT, notes TEXT NOT NULL,
                    version INTEGER NOT NULL, updatedAt INTEGER NOT NULL, syncState TEXT NOT NULL
                )"""
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_study_plans_startsAt ON study_plans (startsAt)"
                    )
                }
            }
        @Volatile private var instance: ClassFlowDatabase? = null

        fun get(context: Context): ClassFlowDatabase =
            instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                ClassFlowDatabase::class.java,
                                "classflow.db",
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build()
                            .also { instance = it }
                }
    }
}
