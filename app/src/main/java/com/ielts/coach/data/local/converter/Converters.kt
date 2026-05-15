package com.ielts.coach.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ielts.coach.data.model.BandScore
import com.ielts.coach.data.model.IELTSPart

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromIELTSPart(value: IELTSPart): String = value.name

    @TypeConverter
    fun toIELTSPart(value: String): IELTSPart = IELTSPart.valueOf(value)

    @TypeConverter
    fun fromBandScore(value: BandScore): String = gson.toJson(value)

    @TypeConverter
    fun toBandScore(value: String): BandScore = gson.fromJson(value, BandScore::class.java)

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}
