package com.piyush.vault.data.local

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DatabaseTypeConverters {
    @TypeConverter
    fun localDateToText(value: LocalDate?): String? = value?.format(DateTimeFormatter.ISO_LOCAL_DATE)

    @TypeConverter
    fun textToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToText(value: LocalTime?): String? = value?.format(TIME_FORMATTER)

    @TypeConverter
    fun textToLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it, TIME_FORMATTER) }

    private companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
    }
}
