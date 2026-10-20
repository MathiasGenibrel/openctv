package com.openctv.browser.features.adblock.di

import com.openctv.browser.features.adblock.domain.AdBlockEngine
import com.openctv.browser.features.adblock.domain.DefaultAdBlockEngine
import org.koin.dsl.module

val adBlockModule = module {
    single<AdBlockEngine> { DefaultAdBlockEngine() }
}
