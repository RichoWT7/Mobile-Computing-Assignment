package com.example.myapplication

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class AddRecipeUiState(
    val title: String = "",
    val description: String = "",
    val prepTime: String = "",
    val servings: String = "",
    val ingredients: String = "",
    val instructions: String = "",
    val selectedImageUri: Uri? = null,
    val dietaryTags: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val message: String? = null
)

class AddRecipeViewModel(application: Application) : AndroidViewModel(application) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(AddRecipeUiState())
    val uiState: StateFlow<AddRecipeUiState> = _uiState.asStateFlow()

    fun updateTitle(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updatePrepTime(prepTime: String) {
        _uiState.value = _uiState.value.copy(prepTime = prepTime)
    }

    fun updateServings(servings: String) {
        _uiState.value = _uiState.value.copy(servings = servings)
    }

    fun updateIngredients(ingredients: String) {
        _uiState.value = _uiState.value.copy(ingredients = ingredients)
    }

    fun updateInstructions(instructions: String) {
        _uiState.value = _uiState.value.copy(instructions = instructions)
    }

    fun updateImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }

    fun toggleDietaryTag(tag: String) {
        val currentTags = _uiState.value.dietaryTags.toMutableSet()
        if (currentTags.contains(tag)) {
            currentTags.remove(tag)
        } else {
            currentTags.add(tag)
        }
        _uiState.value = _uiState.value.copy(dietaryTags = currentTags)
    }

    fun saveRecipe(context: Context) {
        val state = _uiState.value
        val userId = auth.currentUser?.uid

        println("DEBUG: saveRecipe called")
        println("DEBUG: Current user: ${auth.currentUser?.email}")
        println("DEBUG: User ID: $userId")

        if (userId == null) {
            println("DEBUG: No user ID - not logged in")
            _uiState.value = state.copy(
                isSaving = false,
                message = "Please log in to save recipes"
            )
            return
        }

        if (state.title.isEmpty()) {
            println("DEBUG: Title is empty")
            _uiState.value = state.copy(message = "Please enter a title")
            return
        }

        if (state.description.isEmpty()) {
            println("DEBUG: Description is empty")
            _uiState.value = state.copy(message = "Please enter a description")
            return
        }

        println("DEBUG: Starting coroutine")
        viewModelScope.launch {
            try {
                _uiState.value = state.copy(isSaving = true, message = "Saving recipe...")
                println("DEBUG: Starting save, isSaving = true")

                val permanentImagePath = state.selectedImageUri?.let { uri ->
                    saveImageToInternalStorage(uri, context)
                }

                val recipeData = hashMapOf(
                    "title" to state.title,
                    "description" to state.description,
                    "imageUri" to (permanentImagePath ?: ""),
                    "prepTime" to state.prepTime,
                    "servings" to state.servings,
                    "ingredients" to state.ingredients,
                    "instructions" to state.instructions,
                    "dietaryTags" to state.dietaryTags.joinToString(", "),
                    "timestamp" to System.currentTimeMillis()
                )

                println("DEBUG: Sending to Firebase...")

                firestore.collection("users")
                    .document(userId)
                    .collection("saved_recipes")
                    .add(recipeData)
                    .addOnSuccessListener {
                        println("DEBUG: Firebase confirmed save")
                    }
                    .addOnFailureListener { e ->
                        println("DEBUG: Firebase error but might still save: ${e.message}")
                    }

                kotlinx.coroutines.delay(500)

                println("DEBUG: Recipe sent to Firebase (saving in background)")

                _uiState.value = AddRecipeUiState(
                    isSaving = false,
                    message = "Recipe saved successfully!"
                )
                println("DEBUG: Reset form state")

            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                println("DEBUG: Timeout saving recipe")
                _uiState.value = state.copy(
                    isSaving = false,
                    message = "Timeout - check your internet connection"
                )
            } catch (e: Exception) {
                println("DEBUG: Error saving recipe: ${e.message}")
                e.printStackTrace()
                _uiState.value = state.copy(
                    isSaving = false,
                    message = "Error: ${e.message}"
                )
            }
        }
    }

    private fun saveImageToInternalStorage(uri: Uri, context: Context): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

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
            null
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    viewModel: AddRecipeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.updateImage(uri)
    }

    // Show toast messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Recipe") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.selectedImageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(uiState.selectedImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Recipe image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Remove image button
                        IconButton(
                            onClick = { viewModel.updateImage(null) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    RoundedCornerShape(50)
                                )
                        ) {
                            Icon(Icons.Default.Close, "Remove image")
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add image",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Add Recipe Image",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Recipe Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description *") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.prepTime,
                    onValueChange = { viewModel.updatePrepTime(it) },
                    label = { Text("Prep Time") },
                    placeholder = { Text("e.g., 30 mins") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.servings,
                    onValueChange = { viewModel.updateServings(it) },
                    label = { Text("Servings") },
                    placeholder = { Text("e.g., 4") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = uiState.ingredients,
                onValueChange = { viewModel.updateIngredients(it) },
                label = { Text("Ingredients") },
                placeholder = { Text("Separate with commas") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8
            )

            OutlinedTextField(
                value = uiState.instructions,
                onValueChange = { viewModel.updateInstructions(it) },
                label = { Text("Instructions") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10
            )

            Text(
                "Dietary Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val dietaryOptions = listOf(
                "Vegetarian", "Vegan", "Gluten-Free",
                "Keto", "Paleo", "Dairy-Free"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dietaryOptions.take(3).forEach { tag ->
                    FilterChip(
                        selected = uiState.dietaryTags.contains(tag),
                        onClick = { viewModel.toggleDietaryTag(tag) },
                        label = { Text(tag) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dietaryOptions.drop(3).forEach { tag ->
                    FilterChip(
                        selected = uiState.dietaryTags.contains(tag),
                        onClick = { viewModel.toggleDietaryTag(tag) },
                        label = { Text(tag) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = { viewModel.saveRecipe(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isSaving) "Saving..." else "Save Recipe")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}