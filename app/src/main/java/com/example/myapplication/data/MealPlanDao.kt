package com.example.myapplication.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MealPlanDao {

    @Query("SELECT * FROM meal_plans WHERE userId = :userId AND date = :date ORDER BY CASE mealType WHEN 'Breakfast' THEN 1 WHEN 'Lunch' THEN 2 WHEN 'Dinner' THEN 3 END")
    fun getMealPlansForDate(userId: String, date: String): LiveData<List<MealPlan>>

    @Query("SELECT * FROM meal_plans WHERE userId = :userId ORDER BY date DESC")
    fun getAllMealPlans(userId: String): LiveData<List<MealPlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mealPlan: MealPlan)

    @Delete
    suspend fun delete(mealPlan: MealPlan)

    @Query("DELETE FROM meal_plans WHERE userId = :userId AND date = :date AND mealType = :mealType")
    suspend fun deleteMealPlan(userId: String, date: String, mealType: String)
}