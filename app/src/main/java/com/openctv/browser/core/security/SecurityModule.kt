package com.openctv.browser.core.security

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.openctv.browser.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.securityDataStore by preferencesDataStore(name = "security")

val securityModule = module {
    single<SiteExceptionsStore> { DataStoreSiteExceptionsStore(androidContext().securityDataStore) }
    single<SecurityExceptionsContract> {
        val dispatchers = get<DispatcherProvider>()
        SecurityExceptionsImpl(
            store = get(),
            dispatchers = dispatchers,
            scope = CoroutineScope(SupervisorJob() + dispatchers.default),
        )
    }
}
