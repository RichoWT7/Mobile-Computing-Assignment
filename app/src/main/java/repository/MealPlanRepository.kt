package com.example.myapplication.repository

import androidx.lifecycle.LiveData
import com.example.myapplication.data.MealPlan
import com.example.myapplication.data.MealPlanDao

class MealPlanRepository(private val mealPlanDao: MealPlanDao) {

    fun getMealPlansForDate(userId: String, date: String): LiveData<List<MealPlan>> {
        return mealPlanDao.getMealPlansForDate(userId, date)
    }

    fun getAllMealPlans(userId: String): LiveData<List<MealPlan>> {
        return mealPlanDao.getAllMealPlans(userId)
    }

    suspend fun insert(mealPlan: MealPlan) {
        mealPlanDao.insert(mealPlan)
    }

    suspend fun delete(mealPlan: MealPlan) {
        mealPlanDao.delete(mealPlan)
    }

    suspend fun deleteMealPlan(userId: String, date: String, mealType: String) {
        mealPlanDao.deleteMealPlan(userId, date, mealType)
    }
}