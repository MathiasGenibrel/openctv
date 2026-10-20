package com.openctv.browser.features.bookmarks.di

import com.openctv.browser.core.database.OpenCtvDatabase
import com.openctv.browser.features.bookmarks.data.BookmarksImpl
import com.openctv.browser.features.bookmarks.domain.BookmarksContract
import org.koin.dsl.module

val bookmarksModule = module {
    single { get<OpenCtvDatabase>().bookmarkDao() }
    single<BookmarksContract> { BookmarksImpl(dao = get(), dispatchers = get()) }
}
