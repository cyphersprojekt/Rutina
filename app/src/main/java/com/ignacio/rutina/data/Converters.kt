package com.ignacio.rutina.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)

    /** Las horas se guardan como minutos desde la medianoche. */
    @TypeConverter
    fun timeToMinutes(time: LocalTime?): Int? = time?.let { it.hour * 60 + it.minute }

    @TypeConverter
    fun minutesToTime(minutes: Int?): LocalTime? = minutes?.let { LocalTime.of(it / 60, it % 60) }

    @TypeConverter
    fun timesToText(times: List<LocalTime>): String = times.joinToString(",") { timeToMinutes(it).toString() }

    @TypeConverter
    fun textToTimes(text: String): List<LocalTime> =
        if (text.isBlank()) emptyList() else text.split(",").map { minutesToTime(it.toInt())!! }
}
