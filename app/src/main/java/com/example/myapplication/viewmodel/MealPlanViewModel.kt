package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.MealPlan
import com.example.myapplication.repository.MealPlanRepository
import kotlinx.coroutines.launch

class MealPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MealPlanRepository

    init {
        val mealPlanDao = AppDatabase.getDatabase(application).mealPlanDao()
        repository = MealPlanRepository(mealPlanDao)
    }

    fun getMealPlansForDate(userId: String, date: String): LiveData<List<MealPlan>> {
        return repository.getMealPlansForDate(userId, date)
    }

    fun getAllMealPlans(userId: String): LiveData<List<MealPlan>> {
        return repository.getAllMealPlans(userId)
    }

    fun insert(mealPlan: MealPlan) = viewModelScope.launch {
        repository.insert(mealPlan)
    }

    fun delete(mealPlan: MealPlan) = viewModelScope.launch {
        repository.delete(mealPlan)
    }

    fun deleteMealPlan(userId: String, date: String, mealType: String) = viewModelScope.launch {
        repository.deleteMealPlan(userId, date, mealType)
    }
}