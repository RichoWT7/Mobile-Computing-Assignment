package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.myapplication.R
import com.example.myapplication.data.Recipe
import java.io.File

class RecipeSelectionAdapter(
    private val recipes: List<Recipe>,
    private val onRecipeClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeSelectionAdapter.RecipeViewHolder>() {

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.recipe_selection_image)
        val titleView: TextView = view.findViewById(R.id.recipe_selection_title)
        val descriptionView: TextView = view.findViewById(R.id.recipe_selection_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe_selection, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]

        holder.titleView.text = recipe.title
        holder.descriptionView.text = recipe.description

        // Load image
        if (!recipe.imageUri.isNullOrEmpty()) {
            val imageFile = File(recipe.imageUri)
            if (imageFile.exists()) {
                holder.imageView.load(imageFile) {
                    crossfade(true)
                    placeholder(R.drawable.ic_placeholder)
                }
            } else {
                holder.imageView.setImageResource(R.drawable.ic_placeholder)
            }
        } else {
            holder.imageView.setImageResource(R.drawable.ic_placeholder)
        }

        holder.itemView.setOnClickListener {
            onRecipeClick(recipe)
        }
    }

    override fun getItemCount() = recipes.size
}