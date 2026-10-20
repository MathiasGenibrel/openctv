package com.openctv.browser.features.bookmarks.di

import com.openctv.browser.features.bookmarks.data.BookmarksContract
import com.openctv.browser.features.bookmarks.data.BookmarksImpl
import org.koin.dsl.module

val bookmarksModule = module {
    single<BookmarksContract> { BookmarksImpl(dao = get(), dispatchers = get()) }
}
