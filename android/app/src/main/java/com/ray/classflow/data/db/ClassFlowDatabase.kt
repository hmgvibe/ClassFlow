package com.ray.classflow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CourseEntity::class,
        TimetableSlotEntity::class,
        AgendaEntity::class,
        AgendaLinkEntity::class,
        PendingMutationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ClassFlowDatabase : RoomDatabase() {
    abstract fun dao(): ClassFlowDao

    companion object {
        @Volatile private var instance: ClassFlowDatabase? = null

        fun get(context: Context): ClassFlowDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ClassFlowDatabase::class.java,
                "classflow.db",
            ).build().also { instance = it }
        }
    }
}

