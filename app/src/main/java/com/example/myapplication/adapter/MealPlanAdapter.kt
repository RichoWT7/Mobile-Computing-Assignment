package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.myapplication.R
import com.example.myapplication.data.MealPlan
import java.io.File

class MealPlanAdapter(
    private var mealPlans: List<MealPlan>,
    private val onAddClick: (String) -> Unit,
    private val onDeleteClick: (MealPlan) -> Unit
) : RecyclerView.Adapter<MealPlanAdapter.MealPlanViewHolder>() {

    private var currentDate: String = ""
    private val mealTypes = listOf("Breakfast", "Lunch", "Dinner")
    private var expandedPosition: Int = -1

    class MealPlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val mealTypeLabel: TextView = view.findViewById(R.id.meal_type_label)
        val recipeImage: ImageView = view.findViewById(R.id.meal_recipe_image)
        val recipeTitle: TextView = view.findViewById(R.id.meal_recipe_title)
        val recipeDetails: TextView = view.findViewById(R.id.meal_recipe_details)
        val addButton: ImageButton = view.findViewById(R.id.add_meal_button)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_meal_button)
        val expandButton: ImageButton = view.findViewById(R.id.expand_meal_button)
        val emptyView: View = view.findViewById(R.id.empty_meal_view)
        val filledView: View = view.findViewById(R.id.filled_meal_view)

        // Expanded details
        val expandedView: View = view.findViewById(R.id.expanded_meal_view)
        val expandedImage: ImageView = view.findViewById(R.id.expanded_meal_image)
        val expandedDescription: TextView = view.findViewById(R.id.expanded_meal_description)
        val expandedPrepTime: TextView = view.findViewById(R.id.expanded_meal_prep_time)
        val expandedServings: TextView = view.findViewById(R.id.expanded_meal_servings)
        val expandedIngredients: TextView = view.findViewById(R.id.expanded_meal_ingredients)
        val expandedInstructions: TextView = view.findViewById(R.id.expanded_meal_instructions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealPlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal_plan, parent, false)
        return MealPlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: MealPlanViewHolder, position: Int) {
        val mealType = mealTypes[position]
        val mealPlan = mealPlans.find { it.mealType == mealType }

        holder.mealTypeLabel.text = "$mealType:"

        if (mealPlan != null) {
            // Show filled view
            holder.emptyView.visibility = View.GONE
            holder.filledView.visibility = View.VISIBLE

            holder.recipeTitle.text = mealPlan.recipeTitle
            holder.recipeDetails.text = buildString {
                if (mealPlan.recipePrepTime?.isNotEmpty() == true) {
                    append("⏱ ${mealPlan.recipePrepTime}")
                }
                if (mealPlan.recipeServings?.isNotEmpty() == true) {
                    if (isNotEmpty()) append(" • ")
                    append("🍽 ${mealPlan.recipeServings}")
                }
            }

            // Load image
            if (!mealPlan.recipeImageUri.isNullOrEmpty()) {
                val imageFile = File(mealPlan.recipeImageUri)
                if (imageFile.exists()) {
                    holder.recipeImage.load(imageFile) {
                        crossfade(true)
                        placeholder(R.drawable.ic_placeholder)
                    }
                } else {
                    holder.recipeImage.setImageResource(R.drawable.ic_placeholder)
                }
            } else {
                holder.recipeImage.setImageResource(R.drawable.ic_placeholder)
            }

            // Handle expand/collapse
            val isExpanded = position == expandedPosition
            holder.expandedView.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.expandButton.rotation = if (isExpanded) 180f else 0f

            // Load expanded details
            if (isExpanded) {
                // Load large image
                if (!mealPlan.recipeImageUri.isNullOrEmpty()) {
                    val imageFile = File(mealPlan.recipeImageUri)
                    if (imageFile.exists()) {
                        holder.expandedImage.load(imageFile) {
                            crossfade(true)
                            placeholder(R.drawable.ic_placeholder)
                        }
                    } else {
                        holder.expandedImage.setImageResource(R.drawable.ic_placeholder)
                    }
                } else {
                    holder.expandedImage.setImageResource(R.drawable.ic_placeholder)
                }

                holder.expandedDescription.text = mealPlan.recipeDescription
                holder.expandedPrepTime.text = "Prep Time: ${mealPlan.recipePrepTime ?: "N/A"}"
                holder.expandedServings.text = "Servings: ${mealPlan.recipeServings ?: "N/A"}"
                holder.expandedIngredients.text = mealPlan.recipeIngredients?.replace(",", "\n• ") ?: "No ingredients listed"
                holder.expandedInstructions.text = mealPlan.recipeInstructions ?: "No instructions provided"
            }

            // Toggle expand on card click or expand button click
            holder.filledView.setOnClickListener {
                toggleExpanded(position)
            }

            holder.expandButton.setOnClickListener {
                toggleExpanded(position)
            }

            holder.deleteButton.setOnClickListener {
                onDeleteClick(mealPlan)
            }
        } else {
            // Show empty view with add button
            holder.emptyView.visibility = View.VISIBLE
            holder.filledView.visibility = View.GONE
            holder.expandedView.visibility = View.GONE

            holder.addButton.setOnClickListener {
                onAddClick(mealType)
            }
        }
    }

    private fun toggleExpanded(position: Int) {
        val previousExpandedPosition = expandedPosition
        expandedPosition = if (expandedPosition == position) -1 else position

        // Notify changes
        notifyItemChanged(previousExpandedPosition)
        notifyItemChanged(position)
    }

    override fun getItemCount() = mealTypes.size

    fun updateMealPlans(newMealPlans: List<MealPlan>, date: String) {
        mealPlans = newMealPlans
        currentDate = date
        expandedPosition = -1 // Reset expansion when date changes
        notifyDataSetChanged()
    }
}