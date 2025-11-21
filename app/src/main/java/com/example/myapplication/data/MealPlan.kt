package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_plans")
data class MealPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String = "", // Format: "2025-11-17"
    val mealType: String = "", // "Breakfast", "Lunch", "Dinner"
    val recipeTitle: String = "",
    val recipeDescription: String = "",
    val recipeImageUri: String? = null,
    val recipeIngredients: String? = null,
    val recipeInstructions: String? = null,
    val recipePrepTime: String? = null,
    val recipeServings: String? = null,
    val userId: String = "" // To support multiple users
)