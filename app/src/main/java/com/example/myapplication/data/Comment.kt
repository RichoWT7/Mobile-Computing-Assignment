package com.example.myapplication.data

data class Comment(
    val id: String = "",
    val recipeId: String = "",
    val authorEmail: String = "",
    val authorName: String = "",
    val commentText: String = "",
    val timestamp: Long = 0L
)