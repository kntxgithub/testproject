package com.example.caltrack

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.caltrack.data.FoodDatabase
import com.example.caltrack.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: FoodEntryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val database = FoodDatabase.getDatabase(this)
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(database.foodDao())
        )[MainViewModel::class.java]

        setupRecyclerView()
        observeData()

        binding.fabCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = FoodEntryAdapter { entry ->
            AlertDialog.Builder(this)
                .setTitle("削除確認")
                .setMessage("「${entry.foodName}」を削除しますか？")
                .setPositiveButton("削除") { _, _ ->
                    viewModel.deleteEntry(entry)
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun observeData() {
        viewModel.allEntries.observe(this) { entries ->
            adapter.submitList(entries)
            binding.emptyView.visibility =
                if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        viewModel.todayCalories.observe(this) { calories ->
            val total = calories ?: 0
            binding.todayCaloriesText.text = "今日の合計: ${total} kcal"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                AlertDialog.Builder(this)
                    .setTitle("全削除確認")
                    .setMessage("すべての記録を削除しますか？")
                    .setPositiveButton("削除") { _, _ ->
                        viewModel.clearAll()
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
