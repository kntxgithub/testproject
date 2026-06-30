package com.example.caltrack

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.caltrack.data.FoodEntry
import com.example.caltrack.databinding.ItemFoodEntryBinding
import java.io.File

class FoodEntryAdapter(
    private val onDeleteClick: (FoodEntry) -> Unit
) : ListAdapter<FoodEntry, FoodEntryAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemFoodEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: FoodEntry) {
            binding.foodNameText.text = entry.foodName
            binding.caloriesText.text = "${entry.calories} kcal"
            binding.amountText.text = entry.amount
            binding.timeText.text = entry.getFormattedTime()

            val imageFile = File(entry.imagePath)
            if (imageFile.exists()) {
                binding.foodImage.load(imageFile) {
                    crossfade(true)
                    placeholder(R.drawable.ic_food_placeholder)
                }
            } else {
                binding.foodImage.setImageResource(R.drawable.ic_food_placeholder)
            }

            binding.deleteButton.setOnClickListener {
                onDeleteClick(entry)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFoodEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FoodEntry>() {
        override fun areItemsTheSame(oldItem: FoodEntry, newItem: FoodEntry) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FoodEntry, newItem: FoodEntry) =
            oldItem == newItem
    }
}
