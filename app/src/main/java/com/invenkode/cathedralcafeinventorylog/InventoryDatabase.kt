package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [InventoryItem::class], version = 3)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile private var INSTANCE: InventoryDatabase? = null

        fun getDatabase(context: Context): InventoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InventoryDatabase::class.java,
                    "inventory_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE inventory_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        variant TEXT NOT NULL,
                        expirationDate INTEGER NOT NULL,
                        quantity INTEGER NOT NULL,
                        storageType TEXT NOT NULL,
                        stockSubCategory TEXT NOT NULL,
                        stockAlertType TEXT NOT NULL,
                        idealThreshold INTEGER,  -- ✅ nullable
                        isRunningLow INTEGER NOT NULL,
                        notifiedApproaching INTEGER NOT NULL,
                        notifiedExpired INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO inventory_new (
                        id, name, variant, expirationDate, quantity,
                        storageType, stockSubCategory, stockAlertType,
                        idealThreshold, isRunningLow, notifiedApproaching, notifiedExpired
                    )
                    SELECT
                        id, name, variant, expirationDate, quantity,
                        storageType, stockSubCategory, stockAlertType,
                        idealThreshold, isRunningLow, notifiedApproaching, notifiedExpired
                    FROM inventory
                """.trimIndent())

                db.execSQL("DROP TABLE inventory")
                db.execSQL("ALTER TABLE inventory_new RENAME TO inventory")
            }
        }
    }
}

