package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Student::class, Transaction::class, DailyClosing::class, AppUser::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE students ADD COLUMN status TEXT NOT NULL DEFAULT 'Active'")
                db.execSQL("ALTER TABLE students ADD COLUMN photoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN voucherNo TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Students table additions
                db.execSQL("ALTER TABLE students ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE students ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE students ADD COLUMN createdBy TEXT NOT NULL DEFAULT 'Staff'")
                db.execSQL("ALTER TABLE students ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE students ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_students_status ON students (status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_students_status_className ON students (status, className)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_students_isDeleted ON students (isDeleted)")

                // Transactions table additions
                db.execSQL("ALTER TABLE transactions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isDeleted ON transactions (isDeleted)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_monthOfFee_studentId ON transactions (monthOfFee, studentId)")

                // Daily closings table additions
                db.execSQL("ALTER TABLE daily_closings ADD COLUMN closedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_closings_dateString ON daily_closings (dateString)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bls_school_database"
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

