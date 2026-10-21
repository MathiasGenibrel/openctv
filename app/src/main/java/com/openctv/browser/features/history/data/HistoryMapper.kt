package com.openctv.browser.features.history.data

import com.openctv.browser.features.history.domain.HistoryEntry

fun HistoryEntryEntity.toDomain() = HistoryEntry(id = id, url = url, title = title, visitedAt = visitedAt)
