package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapter.RecipeAdapter
import com.example.myapplication.data.Recipe
import com.example.myapplication.viewmodel.RecipeViewModel

class SavedFragment : Fragment() {
    private val recipeViewModel: RecipeViewModel by viewModels()
    private lateinit var adapter: RecipeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_saved, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recipes_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(context, 2)

        adapter = RecipeAdapter(
            recipes = emptyList(),
            onRecipeClick = { recipe ->
                // Handle recipe click - you can add detail view later
            },
            onRecipeLongClick = { recipe ->
                // Show delete confirmation dialog
                showDeleteDialog(recipe)
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
}