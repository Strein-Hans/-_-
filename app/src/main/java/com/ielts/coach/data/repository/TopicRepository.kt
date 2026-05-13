package com.ielts.coach.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ielts.coach.data.model.IELTSTopic
import java.io.InputStreamReader

class TopicRepository(private val context: Context) {

    private val gson = Gson()

    fun loadTopics(fileName: String = "topics/ielts_topics.json"): List<IELTSTopic> {
        return try {
            val inputStream = context.assets.open(fileName)
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<IELTSTopic>>() {}.type
            gson.fromJson(reader, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getTopicsByCategory(topics: List<IELTSTopic>): Map<String, List<IELTSTopic>> {
        return topics.groupBy { it.category }
    }

    fun getRandomTopic(topics: List<IELTSTopic>): IELTSTopic? {
        return topics.randomOrNull()
    }
}
