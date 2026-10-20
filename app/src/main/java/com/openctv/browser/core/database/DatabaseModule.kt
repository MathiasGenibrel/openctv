package com.openctv.browser.core.database

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), OpenCtvDatabase::class.java, "openctv.db").build()
    }
    single { get<OpenCtvDatabase>().bookmarkDao() }
}
