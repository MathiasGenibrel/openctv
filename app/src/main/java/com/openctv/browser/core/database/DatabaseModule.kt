package com.openctv.browser.core.database

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Fournit la base. Chaque feature expose elle-même son DAO dans son module DI. */
val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), OpenCtvDatabase::class.java, "openctv.db")
            // Jamais de migration destructive : elle effacerait les favoris.
            .addMigrations(Migration1To2)
            .build()
    }
}
