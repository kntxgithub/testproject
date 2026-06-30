package com.example.caltrack.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class FoodAnalysisResult(
    val foods: List<FoodItem>,
    val totalCalories: Int,
    val note: String
)

data class FoodItem(
    val name: String,
    val calories: Int,
    val amount: String
)

class ClaudeApiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun analyzeFood(base64Image: String, mediaType: String = "image/jpeg"): Result<FoodAnalysisResult> =
        withContext(Dispatchers.IO) {
            try {
                val requestJson = buildRequestJson(base64Image, mediaType)
                val requestBody = requestJson.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("API error ${response.code}: $responseBody"))
                }

                val result = parseResponse(responseBody)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun buildRequestJson(base64Image: String, mediaType: String): String {
        val request = mapOf(
            "model" to "claude-opus-4-8",
            "max_tokens" to 1024,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "image",
                            "source" to mapOf(
                                "type" to "base64",
                                "media_type" to mediaType,
                                "data" to base64Image
                            )
                        ),
                        mapOf(
                            "type" to "text",
                            "text" to """この画像の食べ物を分析してください。必ずJSON形式のみで返してください（説明文不要）:
{"foods": [{"name": "食べ物名", "calories": カロリー数値, "amount": "量の説明"}], "total_calories": 合計カロリー数値, "note": "補足説明"}

食べ物が見えない場合: {"foods": [], "total_calories": 0, "note": "食べ物が検出されませんでした"}"""
                        )
                    )
                )
            )
        )
        return gson.toJson(request)
    }

    private fun parseResponse(responseBody: String): FoodAnalysisResult {
        val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
        val contentArray = jsonResponse.getAsJsonArray("content")
        val textContent = contentArray.firstOrNull {
            it.asJsonObject.get("type").asString == "text"
        }?.asJsonObject?.get("text")?.asString ?: ""

        // Extract JSON from the response text
        val jsonStart = textContent.indexOf('{')
        val jsonEnd = textContent.lastIndexOf('}') + 1
        val jsonStr = if (jsonStart >= 0 && jsonEnd > jsonStart) {
            textContent.substring(jsonStart, jsonEnd)
        } else {
            throw Exception("Invalid response format")
        }

        val resultJson = JsonParser.parseString(jsonStr).asJsonObject
        val foods = resultJson.getAsJsonArray("foods").map { foodElement ->
            val foodObj = foodElement.asJsonObject
            FoodItem(
                name = foodObj.get("name").asString,
                calories = foodObj.get("calories").asInt,
                amount = foodObj.get("amount").asString
            )
        }

        return FoodAnalysisResult(
            foods = foods,
            totalCalories = resultJson.get("total_calories").asInt,
            note = resultJson.get("note")?.asString ?: ""
        )
    }
}
