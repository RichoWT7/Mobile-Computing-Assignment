package com.example.myapplication.data

data class Recipe(
    val id: Int = 0,
    val title: String,
    val description: String,
    val imageUri: String?,
    val ingredients: String?,
    val instructions: String?,
    val prepTime: String?,
    val servings: String?,
    val firestoreId: String? = null,
    val dietaryTags: String? = null
)