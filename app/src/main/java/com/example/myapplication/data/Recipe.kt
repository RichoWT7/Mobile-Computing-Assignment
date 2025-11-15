package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String,
    val imageUri: String? = null,
    val ingredients: String? = null,
    val instructions: String? = null,
    val prepTime: String? = null,
    val servings: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)