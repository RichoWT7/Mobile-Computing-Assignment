package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AddFragment : Fragment() {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedImageUri: Uri? = null
    private lateinit var imagePreview: ImageView

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            imagePreview.load(it)

            // Show change button, hide FAB
            view?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.add_image_fab)?.visibility = View.GONE
            view?.findViewById<Button>(R.id.change_image_btn)?.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add, container, false)

        imagePreview = view.findViewById(R.id.image_preview)
        val titleInput = view.findViewById<TextInputEditText>(R.id.recipe_title_input)
        val descriptionInput = view.findViewById<TextInputEditText>(R.id.recipe_description_input)
        val prepTimeInput = view.findViewById<TextInputEditText>(R.id.recipe_prep_time_input)
        val servingsInput = view.findViewById<TextInputEditText>(R.id.recipe_servings_input)
        val ingredientsInput = view.findViewById<TextInputEditText>(R.id.recipe_ingredients_input)
        val instructionsInput = view.findViewById<TextInputEditText>(R.id.recipe_instructions_input)
        val addImageFab = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.add_image_fab)
        val changeImageBtn = view.findViewById<Button>(R.id.change_image_btn)
        val saveBtn = view.findViewById<Button>(R.id.save_recipe_btn)

        val chipVegetarian = view.findViewById<Chip>(R.id.chip_vegetarian)
        val chipVegan = view.findViewById<Chip>(R.id.chip_vegan)
        val chipGlutenFree = view.findViewById<Chip>(R.id.chip_gluten_free)
        val chipKeto = view.findViewById<Chip>(R.id.chip_keto)
        val chipPaleo = view.findViewById<Chip>(R.id.chip_paleo)
        val chipDairyFree = view.findViewById<Chip>(R.id.chip_dairy_free)

        addImageFab.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Change image button click
        changeImageBtn.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        saveBtn.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            val prepTime = prepTimeInput.text.toString().trim()
            val servings = servingsInput.text.toString().trim()
            val ingredients = ingredientsInput.text.toString().trim()
            val instructions = instructionsInput.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (description.isEmpty()) {
                Toast.makeText(context, "Please enter a description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dietaryTags = mutableListOf<String>()
            if (chipVegetarian.isChecked) dietaryTags.add("Vegetarian")
            if (chipVegan.isChecked) dietaryTags.add("Vegan")
            if (chipGlutenFree.isChecked) dietaryTags.add("Gluten-Free")
            if (chipKeto.isChecked) dietaryTags.add("Keto")
            if (chipPaleo.isChecked) dietaryTags.add("Paleo")
            if (chipDairyFree.isChecked) dietaryTags.add("Dairy-Free")

            saveRecipeToFirebase(
                title = title,
                description = description,
                prepTime = prepTime,
                servings = servings,
                ingredients = ingredients,
                instructions = instructions,
                dietaryTags = dietaryTags.joinToString(", "),
                titleInput = titleInput,
                descriptionInput = descriptionInput,
                prepTimeInput = prepTimeInput,
                servingsInput = servingsInput,
                ingredientsInput = ingredientsInput,
                instructionsInput = instructionsInput,
                chipVegetarian = chipVegetarian,
                chipVegan = chipVegan,
                chipGlutenFree = chipGlutenFree,
                chipKeto = chipKeto,
                chipPaleo = chipPaleo,
                chipDairyFree = chipDairyFree
            )
        }

        return view
    }

    private fun saveRecipeToFirebase(
        title: String,
        description: String,
        prepTime: String,
        servings: String,
        ingredients: String,
        instructions: String,
        dietaryTags: String,
        titleInput: TextInputEditText,
        descriptionInput: TextInputEditText,
        prepTimeInput: TextInputEditText,
        servingsInput: TextInputEditText,
        ingredientsInput: TextInputEditText,
        instructionsInput: TextInputEditText,
        chipVegetarian: Chip,
        chipVegan: Chip,
        chipGlutenFree: Chip,
        chipKeto: Chip,
        chipPaleo: Chip,
        chipDairyFree: Chip
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "Please log in to save recipes", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(context, "Saving recipe...", Toast.LENGTH_SHORT).show()

                val permanentImagePath = selectedImageUri?.let { uri ->
                    saveImageToInternalStorage(uri)
                }

                val recipeData = hashMapOf(
                    "title" to title,
                    "description" to description,
                    "imageUri" to (permanentImagePath ?: ""),
                    "prepTime" to prepTime,
                    "servings" to servings,
                    "ingredients" to ingredients,
                    "instructions" to instructions,
                    "dietaryTags" to dietaryTags,
                    "timestamp" to System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(userId)
                    .collection("saved_recipes")
                    .add(recipeData)
                    .await()

                titleInput.text?.clear()
                descriptionInput.text?.clear()
                prepTimeInput.text?.clear()
                servingsInput.text?.clear()
                ingredientsInput.text?.clear()
                instructionsInput.text?.clear()
                selectedImageUri = null
                imagePreview.setImageResource(R.drawable.ic_placeholder)

                view?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.add_image_fab)?.visibility = View.VISIBLE
                view?.findViewById<Button>(R.id.change_image_btn)?.visibility = View.GONE

                chipVegetarian.isChecked = false
                chipVegan.isChecked = false
                chipGlutenFree.isChecked = false
                chipKeto.isChecked = false
                chipPaleo.isChecked = false
                chipDairyFree.isChecked = false

                Toast.makeText(context, "Recipe saved!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(context, "Error saving recipe: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val context = requireContext()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Create a unique filename
            val fileName = "recipe_${UUID.randomUUID()}.jpg"
            val directory = File(context.filesDir, "recipe_images")

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, fileName)

            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error saving image", Toast.LENGTH_SHORT).show()
            null
        }
    }
}