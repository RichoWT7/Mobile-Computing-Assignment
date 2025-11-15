package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapter.RecipeExpandableAdapter
import com.example.myapplication.data.CommunityRecipe
import com.example.myapplication.data.Recipe
import com.example.myapplication.viewmodel.RecipeViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class SavedFragment : Fragment() {
    private val recipeViewModel: RecipeViewModel by viewModels()
    private lateinit var adapter: RecipeExpandableAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_saved, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recipes_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = RecipeExpandableAdapter(
            recipes = emptyList(),
            onDeleteClick = { recipe ->
                showDeleteDialog(recipe)
            },
            onShareToCommunity = { recipe ->
                shareToCommunity(recipe)
            },
            onShareSuccess = { recipe ->
                println("Recipe '${recipe.title}' was shared externally!")
            }
        )
        recyclerView.adapter = adapter

        recipeViewModel.allRecipes.observe(viewLifecycleOwner) { recipes ->
            adapter.updateRecipes(recipes)
        }

        return view
    }

    private fun showDeleteDialog(recipe: Recipe) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Recipe")
            .setMessage("Are you sure you want to delete '${recipe.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                recipeViewModel.delete(recipe)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareToCommunity(recipe: Recipe) {
        AlertDialog.Builder(requireContext())
            .setTitle("Share to Community")
            .setMessage("Share '${recipe.title}' with the community?")
            .setPositiveButton("Share") { _, _ ->
                uploadRecipeToCommunity(recipe)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProgressDialog(message: String, progress: Int = -1) {
        if (progressDialog == null) {
            val dialogView = LayoutInflater.from(requireContext()).inflate(
                android.R.layout.activity_list_item, null
            )

            val layout = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(60, 40, 60, 40)
            }

            progressText = TextView(requireContext()).apply {
                text = message
                textSize = 16f
                setPadding(0, 0, 0, 20)
            }

            progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = progress < 0
                max = 100
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            layout.addView(progressText)
            layout.addView(progressBar)

            progressDialog = AlertDialog.Builder(requireContext())
                .setTitle("Sharing Recipe")
                .setView(layout)
                .setCancelable(false)
                .create()

            progressDialog?.show()
        }

        progressText?.text = message
        if (progress >= 0) {
            progressBar?.isIndeterminate = false
            progressBar?.progress = progress
        } else {
            progressBar?.isIndeterminate = true
        }
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressText = null
    }

    private fun uploadRecipeToCommunity(recipe: Recipe) {
        lifecycleScope.launch {
            try {
                showProgressDialog("Saving recipe to community...")

                val imageUrl = ""

                val communityRecipe = CommunityRecipe(
                    title = recipe.title,
                    description = recipe.description,
                    imageUrl = imageUrl,
                    ingredients = recipe.ingredients ?: "",
                    instructions = recipe.instructions ?: "",
                    prepTime = recipe.prepTime ?: "",
                    servings = recipe.servings ?: "",
                    authorEmail = auth.currentUser?.email ?: "Anonymous",
                    authorName = auth.currentUser?.email?.substringBefore("@") ?: "Anonymous",
                    timestamp = System.currentTimeMillis()
                )

                kotlinx.coroutines.withTimeout(30000) { // 30 second timeout
                    firestore.collection("community_recipes")
                        .add(communityRecipe)
                        .await()
                }

                dismissProgressDialog()
                Toast.makeText(context, "Recipe shared to community!", Toast.LENGTH_LONG).show()

            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                dismissProgressDialog()
                Toast.makeText(context, "Upload timed out. Check your internet connection.", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } catch (e: com.google.firebase.FirebaseException) {
                dismissProgressDialog()
                Toast.makeText(context, "Firebase error: ${e.message}\nCheck Firestore rules.", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } catch (e: Exception) {
                dismissProgressDialog()
                Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissProgressDialog()
    }
}