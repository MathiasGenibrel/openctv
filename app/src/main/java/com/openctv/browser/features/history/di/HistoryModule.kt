package com.openctv.browser.features.history.di

import com.openctv.browser.core.database.OpenCtvDatabase
import com.openctv.browser.core.history.PageVisitsContract
import com.openctv.browser.features.history.data.HistoryImpl
import com.openctv.browser.features.history.domain.HistoryContract
import org.koin.dsl.module

val historyModule = module {
    single { get<OpenCtvDatabase>().historyDao() }
    single { HistoryImpl(dao = get(), dispatchers = get()) }
    single<HistoryContract> { get<HistoryImpl>() }
    single<PageVisitsContract> { get<HistoryImpl>() }
}
