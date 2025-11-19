package com.example.myapplication.adapter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.myapplication.R
import com.example.myapplication.data.Recipe
import java.io.File

class RecipeExpandableAdapter(
    private var recipes: List<Recipe>,
    private val onDeleteClick: (Recipe) -> Unit,
    private val onShareToCommunity: (Recipe) -> Unit,
    private val onShareSuccess: ((Recipe) -> Unit)? = null
) : RecyclerView.Adapter<RecipeExpandableAdapter.RecipeViewHolder>() {

    companion object {
        private const val SHARE_ACTION = "com.example.myapplication.SHARE_COMPLETE"
    }

    class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val collapsedView: View = view.findViewById(R.id.collapsed_view)
        val expandedView: View = view.findViewById(R.id.expanded_view)
        val expandButton: ImageButton = view.findViewById(R.id.expand_button)

        val imageView: ImageView = view.findViewById(R.id.recipe_image)
        val imageLarge: ImageView = view.findViewById(R.id.recipe_image_large)
        val titleView: TextView = view.findViewById(R.id.recipe_title)
        val descriptionView: TextView = view.findViewById(R.id.recipe_description)
        val prepTimeView: TextView = view.findViewById(R.id.recipe_prep_time)
        val servingsView: TextView = view.findViewById(R.id.recipe_servings)
        val ingredientsView: TextView = view.findViewById(R.id.recipe_ingredients)
        val instructionsView: TextView = view.findViewById(R.id.recipe_instructions)
        val shareButton: Button = view.findViewById(R.id.share_button)
        val deleteButton: Button = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.expandable_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]

        holder.titleView.text = recipe.title
        holder.descriptionView.text = recipe.description

        // Load images
        if (recipe.imageUri != null) {
            if (recipe.imageUri.startsWith("http://") || recipe.imageUri.startsWith("https://")) {
                // It's a URL (from community) - load directly
                holder.imageView.load(recipe.imageUri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_placeholder)
                    error(R.drawable.ic_placeholder)
                }
                holder.imageLarge.load(recipe.imageUri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_placeholder)
                    error(R.drawable.ic_placeholder)
                }
            } else {
                // It's a local file path
                val imageFile = File(recipe.imageUri)
                if (imageFile.exists()) {
                    holder.imageView.load(imageFile) {
                        crossfade(true)
                        placeholder(R.drawable.ic_placeholder)
                    }
                    holder.imageLarge.load(imageFile) {
                        crossfade(true)
                        placeholder(R.drawable.ic_placeholder)
                    }
                } else {
                    holder.imageView.setImageResource(R.drawable.ic_placeholder)
                    holder.imageLarge.setImageResource(R.drawable.ic_placeholder)
                }
            }
        } else {
            holder.imageView.setImageResource(R.drawable.ic_placeholder)
            holder.imageLarge.setImageResource(R.drawable.ic_placeholder)
        }

        holder.prepTimeView.text = "Prep Time: ${recipe.prepTime ?: "N/A"}"
        holder.servingsView.text = "Servings: ${recipe.servings ?: "N/A"}"
        holder.ingredientsView.text = recipe.ingredients?.replace(",", "\n• ") ?: "No ingredients listed"
        holder.instructionsView.text = recipe.instructions ?: "No instructions provided"

        holder.collapsedView.setOnClickListener {
            toggleExpanded(holder)
        }

        holder.expandButton.setOnClickListener {
            toggleExpanded(holder)
        }

        holder.shareButton.setOnClickListener {
            showShareMenu(it, recipe)
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(recipe)
        }
    }

    private fun toggleExpanded(holder: RecipeViewHolder) {
        if (holder.expandedView.visibility == View.GONE) {
            holder.expandedView.visibility = View.VISIBLE
            holder.expandButton.rotation = 180f
        } else {
            holder.expandedView.visibility = View.GONE
            holder.expandButton.rotation = 0f
        }
    }

    private fun showShareMenu(view: View, recipe: Recipe) {
        val popupMenu = PopupMenu(view.context, view)
        popupMenu.menuInflater.inflate(R.menu.share_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.share_external -> {
                    shareRecipeExternal(view.context, recipe)
                    true
                }
                R.id.share_community -> {
                    onShareToCommunity(recipe)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun shareRecipeExternal(context: Context, recipe: Recipe) {
        val shareText = buildString {
            append("Check out this recipe: ${recipe.title}\n\n")
            append("${recipe.description}\n\n")
            if (recipe.prepTime != null) append("Prep Time: ${recipe.prepTime}\n")
            if (recipe.servings != null) append("Servings: ${recipe.servings}\n\n")
            if (recipe.ingredients != null) {
                append("Ingredients:\n${recipe.ingredients.replace(",", "\n")}\n\n")
            }
            if (recipe.instructions != null) {
                append("Instructions:\n${recipe.instructions}")
            }
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val componentName = intent?.getParcelableExtra<android.content.ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)

                if (componentName != null) {
                    Toast.makeText(context, "Shared successfully!", Toast.LENGTH_SHORT).show()
                    onShareSuccess?.invoke(recipe)
                } else {
                    Toast.makeText(context, "Share cancelled", Toast.LENGTH_SHORT).show()
                }
                context?.unregisterReceiver(this)
            }
        }

        val filter = IntentFilter(SHARE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(shareReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(shareReceiver, filter)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(SHARE_ACTION),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val chooserIntent = Intent.createChooser(shareIntent, "Share Recipe", pendingIntent.intentSender)
        context.startActivity(chooserIntent)
    }

    override fun getItemCount() = recipes.size

    fun updateRecipes(newRecipes: List<Recipe>) {
        recipes = newRecipes
        notifyDataSetChanged()
    }
}