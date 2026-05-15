package com.ielts.coach.engine.api

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BackendApiClient {

    private const val TAG = "BackendApiClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var baseUrl: String = "http://10.0.2.2:8000"

    fun post(path: String, body: JSONObject, callback: (Result<JSONObject>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "${baseUrl.trimEnd('/')}$path"
                val request = Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    val errMsg = "HTTP ${response.code}: ${responseBody?.take(200)}"
                    Log.e(TAG, "POST $path failed: $errMsg")
                    withContext(Dispatchers.Main) {
                        callback(Result.failure(Exception(errMsg)))
                    }
                    return@launch
                }

                val json = JSONObject(responseBody)
                withContext(Dispatchers.Main) {
                    callback(Result.success(json))
                }
            } catch (e: Exception) {
                Log.e(TAG, "POST $path error", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }
}
