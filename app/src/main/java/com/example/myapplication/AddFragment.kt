package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil.load
import com.example.myapplication.data.Recipe
import com.example.myapplication.viewmodel.RecipeViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AddFragment : Fragment() {
    private val recipeViewModel: RecipeViewModel by viewModels()
    private var selectedImageUri: Uri? = null
    private lateinit var imagePreview: ImageView

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            imagePreview.load(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add, container, false)

        imagePreview = view.findViewById(R.id.image_preview)
        val titleInput = view.findViewById<EditText>(R.id.recipe_title_input)
        val descriptionInput = view.findViewById<EditText>(R.id.recipe_description_input)
        val selectImageBtn = view.findViewById<Button>(R.id.select_image_btn)
        val saveBtn = view.findViewById<Button>(R.id.save_recipe_btn)

        selectImageBtn.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        saveBtn.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save image permanently to internal storage
            val permanentImagePath = selectedImageUri?.let { uri ->
                saveImageToInternalStorage(uri)
            }

            val recipe = Recipe(
                title = title,
                description = description,
                imageUri = permanentImagePath
            )

            recipeViewModel.insert(recipe)

            // Clear form
            titleInput.text.clear()
            descriptionInput.text.clear()
            selectedImageUri = null
            imagePreview.setImageResource(R.drawable.ic_placeholder)

            Toast.makeText(context, "Recipe saved!", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val context = requireContext()
            val inputStream = context.contentResolver.openInputStream(uri)

            // Create a unique filename
            val fileName = "recipe_${UUID.randomUUID()}.jpg"
            val directory = File(context.filesDir, "recipe_images")

            // Create directory if it doesn't exist
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, fileName)

            // Copy the image to internal storage
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            // Return the file path
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}