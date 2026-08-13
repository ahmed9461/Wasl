package com.wasl.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.wasl.app.data.local.dao.DebtDao
import com.wasl.app.data.local.dao.LedgerDao
import com.wasl.app.data.local.dao.PersonDao
import com.wasl.app.data.local.entity.DebtEntity
import com.wasl.app.data.local.entity.LedgerEntryEntity
import com.wasl.app.data.local.entity.PersonEntity

@Database(
    entities = [
        PersonEntity::class,
        DebtEntity::class,
        LedgerEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WaslDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao

    abstract fun debtDao(): DebtDao

    abstract fun ledgerDao(): LedgerDao

    companion object {
        const val DATABASE_NAME = "wasl.db"

        /** Version 1 is the baseline; every future migration is registered here. */
        val ALL_MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): WaslDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WaslDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
