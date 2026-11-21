package com.pockit.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PersonEntity::class,
        NicknameEntity::class,
        GroupSpaceEntity::class,
        GroupMemberEntity::class,
        EntryEntity::class,
        ShareEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PockitDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun nicknameDao(): NicknameDao
    abstract fun groupSpaceDao(): GroupSpaceDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun entryDao(): EntryDao
    abstract fun shareDao(): ShareDao

    companion object {
        @Volatile private var instance: PockitDatabase? = null

        fun get(context: Context): PockitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PockitDatabase::class.java,
                    "pockit.db",
                )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                .also { instance = it }
            }
    }
}
