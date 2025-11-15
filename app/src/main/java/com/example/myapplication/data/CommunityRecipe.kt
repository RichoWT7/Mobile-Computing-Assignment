package com.example.myapplication.data

data class CommunityRecipe(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val ingredients: String = "",
    val instructions: String = "",
    val prepTime: String = "",
    val servings: String = "",
    val authorEmail: String = "",
    val authorName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val likes: Int = 0
)