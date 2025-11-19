package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.Recipe
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SavedViewModel(app: Application) : AndroidViewModel(app) {

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    init {
        loadSavedRecipes()
    }

    private fun loadSavedRecipes() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore
            .collection("users")
            .document(uid)
            .collection("saved_recipes")
            .addSnapshotListener { snap, _ ->
                _recipes.value = snap?.documents?.mapNotNull { doc ->
                    doc.toObject<Recipe>()?.copy(firestoreId = doc.id)
                } ?: emptyList()
            }
    }

    fun delete(recipe: Recipe) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        recipe.firestoreId?.let { id ->
            Firebase.firestore
                .collection("users")
                .document(uid)
                .collection("saved_recipes")
                .document(id)
                .delete()
        }
    }
}