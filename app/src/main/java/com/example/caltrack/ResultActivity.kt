package com.example.caltrack

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.caltrack.api.ClaudeRepository
import com.example.caltrack.api.FoodAnalysisResult
import com.example.caltrack.data.FoodDatabase
import com.example.caltrack.data.FoodEntry
import com.example.caltrack.databinding.ActivityResultBinding
import kotlinx.coroutines.launch
import java.io.File

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var prefs: SharedPreferences
    private var analysisResult: FoodAnalysisResult? = null
    private var imagePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = getSharedPreferences("caltrack_prefs", MODE_PRIVATE)
        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH) ?: ""

        if (imagePath.isEmpty()) {
            Toast.makeText(this, "画像が見つかりません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.foodImage.load(File(imagePath)) {
            crossfade(true)
        }

        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            showApiKeyDialog()
        } else {
            analyzeImage(apiKey)
        }

        binding.saveButton.setOnClickListener { saveEntry() }
        binding.retryButton.setOnClickListener {
            val key = prefs.getString("api_key", "") ?: ""
            if (key.isNotEmpty()) analyzeImage(key)
        }
    }

    private fun showApiKeyDialog() {
        val input = android.widget.EditText(this)
        input.hint = "sk-ant-..."
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Claude APIキーを入力")
            .setMessage("Anthropic APIキーを入力してください。\nhttps://console.anthropic.com でキーを取得できます。")
            .setView(input)
            .setPositiveButton("設定") { _, _ ->
                val key = input.text.toString().trim()
                if (key.startsWith("sk-ant-")) {
                    prefs.edit().putString("api_key", key).apply()
                    analyzeImage(key)
                } else {
                    Toast.makeText(this, "有効なAPIキーを入力してください", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .setNegativeButton("キャンセル") { _, _ -> finish() }
            .show()
    }

    private fun analyzeImage(apiKey: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE
        binding.saveButton.visibility = View.GONE
        binding.retryButton.visibility = View.GONE
        binding.statusText.text = "AIが分析中..."

        lifecycleScope.launch {
            val repository = ClaudeRepository(apiKey)
            val result = repository.analyzeImageFile(imagePath)

            result.onSuccess { foodResult ->
                analysisResult = foodResult
                displayResult(foodResult)
            }.onFailure { error ->
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = "分析に失敗しました"
                binding.retryButton.visibility = View.VISIBLE
                Toast.makeText(
                    this@ResultActivity,
                    "エラー: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun displayResult(result: FoodAnalysisResult) {
        binding.progressBar.visibility = View.GONE
        binding.resultCard.visibility = View.VISIBLE
        binding.saveButton.visibility = View.VISIBLE

        if (result.foods.isEmpty()) {
            binding.foodListText.text = "食べ物が検出されませんでした"
            binding.totalCaloriesText.text = "0 kcal"
        } else {
            val foodList = result.foods.joinToString("\n") { food ->
                "• ${food.name}（${food.amount}）: ${food.calories} kcal"
            }
            binding.foodListText.text = foodList
            binding.totalCaloriesText.text = "${result.totalCalories} kcal"
        }

        binding.noteText.text = result.note
        binding.statusText.text = "分析完了"
    }

    private fun saveEntry() {
        val result = analysisResult ?: return
        if (result.foods.isEmpty()) {
            Toast.makeText(this, "保存する食べ物がありません", Toast.LENGTH_SHORT).show()
            return
        }

        val mainFood = result.foods.first()
        val allFoodNames = result.foods.joinToString("、") { it.name }

        lifecycleScope.launch {
            val database = FoodDatabase.getDatabase(this@ResultActivity)
            val entry = FoodEntry(
                foodName = allFoodNames,
                calories = result.totalCalories,
                amount = result.foods.joinToString("、") { "${it.name}: ${it.amount}" },
                note = result.note,
                imagePath = imagePath
            )
            database.foodDao().insert(entry)
            Toast.makeText(this@ResultActivity, "記録しました！", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
    }
}
