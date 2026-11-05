package io.github.ambroal.climer.data.db

import androidx.room.TypeConverter
import io.github.ambroal.climer.domain.load.LoadSpec
import java.time.LocalDate
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    @TypeConverter
    fun loadSpecToJson(spec: LoadSpec): String = Json.encodeToString(LoadSpec.serializer(), spec)

    @TypeConverter
    fun jsonToLoadSpec(json: String): LoadSpec = Json.decodeFromString(LoadSpec.serializer(), json)
}
