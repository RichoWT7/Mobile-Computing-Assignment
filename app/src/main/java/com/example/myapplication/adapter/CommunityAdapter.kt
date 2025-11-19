package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.myapplication.R
import com.example.myapplication.data.CommunityRecipe

class CommunityAdapter(
    private var recipes: List<CommunityRecipe>,
    private val currentUserEmail: String?,
    private val onViewDetails: (CommunityRecipe) -> Unit,
    private val onSaveToMyRecipes: (CommunityRecipe) -> Unit,
    private val onDeletePost: (CommunityRecipe) -> Unit
) : RecyclerView.Adapter<CommunityAdapter.CommunityViewHolder>() {

    class CommunityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.community_recipe_image)
        val titleView: TextView = view.findViewById(R.id.community_recipe_title)
        val authorView: TextView = view.findViewById(R.id.community_recipe_author)
        val descriptionView: TextView = view.findViewById(R.id.community_recipe_description)
        val prepTimeView: TextView = view.findViewById(R.id.community_recipe_prep_time)
        val servingsView: TextView = view.findViewById(R.id.community_recipe_servings)
        val dietaryTagsView: TextView = view.findViewById(R.id.community_recipe_dietary_tags)
        val viewDetailsButton: Button = view.findViewById(R.id.view_details_button)
        val saveButton: Button = view.findViewById(R.id.save_to_my_recipes_button)
        val deleteButton: Button = view.findViewById(R.id.delete_post_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.community_recipe, parent, false)
        return CommunityViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommunityViewHolder, position: Int) {
        val recipe = recipes[position]

        holder.titleView.text = recipe.title
        holder.authorView.text = "By: ${recipe.authorEmail}"
        holder.descriptionView.text = recipe.description
        holder.prepTimeView.text = "⏱ ${recipe.prepTime.ifEmpty { "N/A" }}"
        holder.servingsView.text = "🍽 ${recipe.servings.ifEmpty { "N/A" }}"

        // Show dietary tags if available
        if (recipe.dietaryTags.isNotEmpty()) {
            holder.dietaryTagsView.visibility = View.VISIBLE
            holder.dietaryTagsView.text = "🏷 ${recipe.dietaryTags}"
        } else {
            holder.dietaryTagsView.visibility = View.GONE
        }

        if (recipe.imageUrl.isNotEmpty()) {
            holder.imageView.load(recipe.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
            }
        } else {
            holder.imageView.setImageResource(R.drawable.ic_placeholder)
        }

        if (recipe.authorEmail == currentUserEmail) {
            holder.deleteButton.visibility = View.VISIBLE
            holder.deleteButton.setOnClickListener {
                onDeletePost(recipe)
            }
        } else {
            holder.deleteButton.visibility = View.GONE
        }

        holder.viewDetailsButton.setOnClickListener {
            onViewDetails(recipe)
        }

        holder.saveButton.setOnClickListener {
            onSaveToMyRecipes(recipe)
        }
    }

    override fun getItemCount() = recipes.size

    fun updateRecipes(newRecipes: List<CommunityRecipe>) {
        recipes = newRecipes
        notifyDataSetChanged()
    }
}