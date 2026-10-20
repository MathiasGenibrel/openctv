package com.openctv.browser.features.search.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.openctv.browser.features.search.data.RecentSearchesImpl
import com.openctv.browser.features.search.domain.RecentSearchesContract
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.recentSearchesDataStore by preferencesDataStore(name = "recent_searches")

val searchModule = module {
    single<RecentSearchesContract> { RecentSearchesImpl(androidContext().recentSearchesDataStore) }
}
