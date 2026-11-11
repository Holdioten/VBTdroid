package com.vbtdroid.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        SetEntity::class,
        RepEntity::class,
        PhaseEntity::class,
        LiftProfileEntity::class,
        OneRmEstimateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN lift TEXT NOT NULL DEFAULT 'SQUAT'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN variant TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE sets ADD COLUMN variant TEXT NOT NULL DEFAULT 'DEFAULT'")
                db.execSQL("ALTER TABLE sets ADD COLUMN loadKg REAL")
                db.execSQL("ALTER TABLE sets ADD COLUMN plateDiameterMm REAL NOT NULL DEFAULT 450.0")
                db.execSQL("ALTER TABLE sets ADD COLUMN cameraAngleDeg REAL NOT NULL DEFAULT 90.0")
                db.execSQL("ALTER TABLE sets ADD COLUMN cameraLevel INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sets ADD COLUMN videoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sets ADD COLUMN mcvAvgMps REAL")
                db.execSQL("ALTER TABLE sets ADD COLUMN exportJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reps ADD COLUMN startSec REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE reps ADD COLUMN endSec REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS phases (" +
                        "phaseId TEXT NOT NULL PRIMARY KEY, repId TEXT NOT NULL, phaseIndex INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, startSec REAL NOT NULL, endSec REAL NOT NULL, manual INTEGER NOT NULL, " +
                        "FOREIGN KEY(repId) REFERENCES reps(repId) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_phases_repId_phaseIndex ON phases(repId, phaseIndex)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS lift_profiles (" +
                        "lift TEXT NOT NULL, variant TEXT NOT NULL, v1rmMps REAL NOT NULL, v1rmSource TEXT NOT NULL, " +
                        "slope REAL, intercept REAL, r2 REAL, estimated1RmKg REAL, pointCount INTEGER NOT NULL, " +
                        "unavailableReason TEXT, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(lift, variant))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sets_lift_variant_createdAtEpochMs ON sets(lift, variant, createdAtEpochMs)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS one_rm_estimates (" +
                        "estimateId TEXT NOT NULL PRIMARY KEY, lift TEXT NOT NULL, variant TEXT NOT NULL, " +
                        "createdAtEpochMs INTEGER NOT NULL, estimateKg REAL NOT NULL, mvtMps REAL NOT NULL, " +
                        "slope REAL NOT NULL, intercept REAL NOT NULL, r2 REAL NOT NULL, " +
                        "pointCount INTEGER NOT NULL, setIds TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_one_rm_estimates_lift_variant_createdAtEpochMs " +
                        "ON one_rm_estimates(lift, variant, createdAtEpochMs)",
                )
            }
        }

        fun build(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "vbtdroid.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
