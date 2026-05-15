package com.ielts.coach.engine.conversation

import android.util.Log
import com.ielts.coach.data.model.IELTSPart
import com.ielts.coach.data.model.IELTSTopic
import com.ielts.coach.engine.api.BackendApiClient
import org.json.JSONArray
import org.json.JSONObject

class BackendConversationProvider : ConversationProvider {

    override fun getResponse(
        userText: String,
        part: IELTSPart,
        topic: IELTSTopic?,
        history: List<String>,
        callback: (String) -> Unit,
    ) {
        val body = JSONObject().apply {
            put("userText", userText)
            put("part", part.name)
            if (topic != null) {
                put("topic", JSONObject().apply {
                    put("id", topic.id)
                    put("category", topic.category)
                    put("topic", topic.topic)
                    put("bulletPoints", JSONArray(topic.bulletPoints))
                    put("followUpQuestions", JSONArray(topic.followUpQuestions))
                    put("difficulty", topic.difficulty)
                })
            }
            val historyArr = JSONArray()
            history.forEachIndexed { index, text ->
                historyArr.put(JSONObject().apply {
                    put("role", if (index % 2 == 0) "assistant" else "user")
                    put("content", text)
                })
            }
            put("history", historyArr)
        }

        BackendApiClient.post("/conversation", body) { result ->
            result.onSuccess { json ->
                val reply = json.optString("response", "Could you say more about that?")
                callback(reply)
            }.onFailure { e ->
                Log.e(TAG, "Backend conversation failed", e)
                callback("I'm sorry, could you please repeat that?")
            }
        }
    }

    companion object {
        private const val TAG = "BackendConversation"
    }
}
