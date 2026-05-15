package com.ielts.coach.engine.conversation

import android.util.Log
import com.ielts.coach.data.model.IELTSPart
import com.ielts.coach.data.model.IELTSTopic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LLMConversationProvider(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String = "deepseek-chat",
) : ConversationProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun getResponse(
        userText: String,
        part: IELTSPart,
        topic: IELTSTopic?,
        history: List<String>,
        callback: (String) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val systemPrompt = buildSystemPrompt(part, topic)
                val messages = buildMessages(systemPrompt, history, userText)

                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("temperature", 0.7)
                    put("max_tokens", 300)
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    withContext(Dispatchers.Main) {
                        callback("I'm sorry, could you please repeat that?")
                    }
                    return@launch
                }

                val examinerReply = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    ?: "Could you say more about that?"

                withContext(Dispatchers.Main) {
                    callback(examinerReply)
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM request failed", e)
                withContext(Dispatchers.Main) {
                    callback("I apologize, could you please elaborate on that?")
                }
            }
        }
    }

    private fun buildSystemPrompt(part: IELTSPart, topic: IELTSTopic?): String {
        val base = "You are a certified IELTS Speaking examiner conducting a Speaking test. " +
                "Be friendly but professional. Ask follow-up questions naturally. " +
                "Keep your responses concise (2-3 sentences). " +
                "Do NOT provide scores or feedback during the test. " +
                "If the student gives a short answer, ask them to elaborate."

        return when (part) {
            IELTSPart.PART_1 -> base + " You are conducting Part 1: Introduction and Interview. " +
                    "Ask about familiar topics like hobbies, studies, work, hometown, etc."

            IELTSPart.PART_2 -> base + " You are conducting Part 2: Individual Long Turn. " +
                    "The topic is: ${topic?.topic ?: "a memorable experience"}. " +
                    "First introduce the topic card, then listen to the candidate speak for 2 minutes."

            IELTSPart.PART_3 -> base + " You are conducting Part 3: Two-way Discussion. " +
                    "The discussion topic is: ${topic?.topic ?: "a general topic"}. " +
                    "Ask abstract and analytical questions related to this topic."
        }
    }

    private fun buildMessages(systemPrompt: String, history: List<String>, currentUserText: String): JSONArray {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // Alternate examiner/user from history
        history.forEachIndexed { index, text ->
            val role = if (index % 2 == 0) "assistant" else "user"
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", text)
            })
        }

        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", currentUserText)
        })

        return messages
    }

    companion object {
        private const val TAG = "LLMConversationProvider"
    }
}
