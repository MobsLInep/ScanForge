package com.scanforge.core.database

import androidx.room.TypeConverter
import java.time.Instant

/** Room type converters for non-primitive column types. */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
