package com.openctv.browser.core.database

import androidx.room.TypeConverter
import java.time.Instant

class InstantConverters {
    @TypeConverter
    fun toEpochMilli(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun toInstant(epochMilli: Long): Instant = Instant.ofEpochMilli(epochMilli)
}
