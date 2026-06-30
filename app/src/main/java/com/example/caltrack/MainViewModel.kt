package com.example.caltrack

import androidx.lifecycle.*
import com.example.caltrack.data.FoodDao
import com.example.caltrack.data.FoodEntry
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(private val dao: FoodDao) : ViewModel() {

    val allEntries = dao.getAllEntries()

    val todayCalories: LiveData<Int?> = dao.getTodayCalories(getStartOfToday())

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch {
            dao.delete(entry)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }

    private fun getStartOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    class Factory(private val dao: FoodDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(dao) as T
        }
    }
}
