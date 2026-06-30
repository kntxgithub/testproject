package com.example.caltrack.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

class ClaudeRepository(apiKey: String) {

    private val apiService = ClaudeApiService(apiKey)

    suspend fun analyzeImageFile(imagePath: String): Result<FoodAnalysisResult> {
        val file = File(imagePath)
        if (!file.exists()) {
            return Result.failure(Exception("画像ファイルが見つかりません"))
        }

        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: return Result.failure(Exception("画像の読み込みに失敗しました"))

        val compressedBitmap = compressBitmap(bitmap)
        val base64 = bitmapToBase64(compressedBitmap)

        return apiService.analyzeFood(base64)
    }

    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val maxDimension = 1024
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
