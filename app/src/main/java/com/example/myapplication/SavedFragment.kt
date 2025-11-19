package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.Recipe
import com.example.myapplication.screen.SavedScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewmodel.SavedViewModel
import com.example.myapplication.util.ImgurUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import java.io.File

class SavedFragment : Fragment() {

    private val viewModel: SavedViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MyApplicationTheme {
                val recipes by viewModel.recipes.collectAsState()
                SavedScreen(
                    recipes = recipes,
                    onDelete = { showDeleteDialog(it) },
                    onShareToCommunity = { shareToCommunity(it) }
                )
            }
        }
    }

    /* ---------- business logic untouched ---------- */

    private fun showDeleteDialog(recipe: Recipe) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Recipe")
            .setMessage("Are you sure you want to delete '${recipe.title}'?")
            .setPositiveButton("Delete") { _, _ -> deleteRecipe(recipe) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecipe(recipe: Recipe) {
        val userId = auth.currentUser?.uid ?: return
        val id = recipe.firestoreId ?: run {
            Toast.makeText(context, "Cannot delete: Recipe ID not found", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            firestore.collection("users").document(userId)
                .collection("saved_recipes").document(id)
                .delete().await()
            Toast.makeText(context, "Recipe deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareToCommunity(recipe: Recipe) {
        AlertDialog.Builder(requireContext())
            .setTitle("Share to Community")
            .setMessage("Share '${recipe.title}' with the community?")
            .setPositiveButton("Share") { _, _ -> uploadRecipeToCommunity(recipe) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProgressDialog(message: String, progress: Int = -1) {
        if (progressDialog == null) {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
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
        progressBar?.isIndeterminate = progress < 0
        progressBar?.progress = progress.coerceAtLeast(0)
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
                showProgressDialog("Preparing to upload...")
                var imageUrl = ""
                recipe.imageUri?.let { uri ->
                    val file = File(uri)
                    if (file.exists()) {
                        showProgressDialog("Uploading image to Imgur...")
                        imageUrl = ImgurUploader.uploadImage(file) ?: ""
                    }
                }
                showProgressDialog("Saving recipe to community...")
                val data = mapOf(
                    "title" to recipe.title,
                    "description" to recipe.description,
                    "imageUrl" to imageUrl,
                    "ingredients" to (recipe.ingredients ?: ""),
                    "instructions" to (recipe.instructions ?: ""),
                    "prepTime" to (recipe.prepTime ?: ""),
                    "servings" to (recipe.servings ?: ""),
                    "authorEmail" to (auth.currentUser?.email ?: "Anonymous"),
                    "authorName" to (auth.currentUser?.email?.substringBefore("@") ?: "Anonymous"),
                    "timestamp" to System.currentTimeMillis()
                )
                withTimeout(30_000) {
                    firestore.collection("community_recipes").add(data).await()
                }
                withContext(Dispatchers.Main) {
                    dismissProgressDialog()
                    Toast.makeText(context, "Recipe shared to community!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dismissProgressDialog()
                    Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissProgressDialog()
    }
}