package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapter.CommunityAdapter
import com.example.myapplication.data.CommunityRecipe
import com.example.myapplication.data.Recipe
import com.example.myapplication.viewmodel.RecipeViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HomeFragment : Fragment() {

    private val recipeViewModel: RecipeViewModel by viewModels()
    private lateinit var adapter: CommunityAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.community_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = CommunityAdapter(
            recipes = emptyList(),
            currentUserEmail = auth.currentUser?.email,
            onViewDetails = { recipe ->
                showRecipeDetails(recipe)
            },
            onSaveToMyRecipes = { communityRecipe ->
                saveToMyRecipes(communityRecipe)
            },
            onDeletePost = { recipe ->
                showDeleteDialog(recipe)
            }
        )
        recyclerView.adapter = adapter

        // Load community recipes from Firestore
        loadCommunityRecipes()

        return view
    }

    private fun loadCommunityRecipes() {
        firestore.collection("community_recipes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Error loading recipes: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val recipes = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CommunityRecipe::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                adapter.updateRecipes(recipes)
            }
    }

    private fun showRecipeDetails(recipe: CommunityRecipe) {
        val message = buildString {
            append("By: ${recipe.authorEmail}\n\n")
            append("${recipe.description}\n\n")
            if (recipe.prepTime.isNotEmpty()) append("Prep Time: ${recipe.prepTime}\n")
            if (recipe.servings.isNotEmpty()) append("Servings: ${recipe.servings}\n\n")
            if (recipe.ingredients.isNotEmpty()) {
                append("Ingredients:\n${recipe.ingredients.replace(",", "\n• ")}\n\n")
            }
            if (recipe.instructions.isNotEmpty()) {
                append("Instructions:\n${recipe.instructions}")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(recipe.title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun saveToMyRecipes(communityRecipe: CommunityRecipe) {
        val recipe = Recipe(
            title = communityRecipe.title,
            description = communityRecipe.description,
            imageUri = null,
            ingredients = communityRecipe.ingredients,
            instructions = communityRecipe.instructions,
            prepTime = communityRecipe.prepTime,
            servings = communityRecipe.servings
        )

        recipeViewModel.insert(recipe)
        Toast.makeText(context, "Recipe saved to your collection!", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteDialog(recipe: CommunityRecipe) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Post")
            .setMessage("Delete '${recipe.title}' from community? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteFromCommunity(recipe)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteFromCommunity(recipe: CommunityRecipe) {
        lifecycleScope.launch {
            try {
                Toast.makeText(context, "Deleting post...", Toast.LENGTH_SHORT).show()

                // Delete from Firestore
                firestore.collection("community_recipes")
                    .document(recipe.id)
                    .delete()
                    .await()

                // Delete image from Storage if exists
                if (recipe.imageUrl.isNotEmpty()) {
                    try {
                        val imageRef = storage.getReferenceFromUrl(recipe.imageUrl)
                        imageRef.delete().await()
                    } catch (e: Exception) {
                        // Image already deleted or doesn't exist
                        e.printStackTrace()
                    }
                }

                Toast.makeText(context, "Post deleted successfully!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
}