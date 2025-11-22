package com.example.myapplication

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myapplication.data.CommunityRecipe
import com.example.myapplication.data.Recipe
import com.example.myapplication.util.ImgurUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

data class SavedUiState(
    val recipes: List<Recipe> = emptyList(),
    val expandedRecipeId: Int? = null,
    val isUploading: Boolean = false,
    val uploadProgress: String = "",
    val message: String? = null
)

class SavedViewModel(application: Application) : AndroidViewModel(application) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(SavedUiState())
    val uiState: StateFlow<SavedUiState> = _uiState.asStateFlow()

    init {
        loadSavedRecipes()
    }

    private fun loadSavedRecipes() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.value = _uiState.value.copy(message = "Please log in to view recipes")
            return
        }

        firestore.collection("users")
            .document(userId)
            .collection("saved_recipes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = _uiState.value.copy(message = "Error loading recipes")
                    return@addSnapshotListener
                }

                val recipes = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Recipe(
                            id = doc.id.hashCode(),
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            imageUri = doc.getString("imageUri"),
                            ingredients = doc.getString("ingredients"),
                            instructions = doc.getString("instructions"),
                            prepTime = doc.getString("prepTime"),
                            servings = doc.getString("servings"),
                            firestoreId = doc.id,
                            dietaryTags = doc.getString("dietaryTags")
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(recipes = recipes)
            }
    }

    fun toggleExpanded(recipeId: Int) {
        _uiState.value = _uiState.value.copy(
            expandedRecipeId = if (_uiState.value.expandedRecipeId == recipeId) null else recipeId
        )
    }

    fun deleteRecipe(recipe: Recipe) {
        val userId = auth.currentUser?.uid
        val firestoreId = recipe.firestoreId

        if (userId == null || firestoreId == null) {
            _uiState.value = _uiState.value.copy(message = "Cannot delete recipe")
            return
        }

        viewModelScope.launch {
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("saved_recipes")
                    .document(firestoreId)
                    .delete()
                    .await()

                _uiState.value = _uiState.value.copy(message = "Recipe deleted")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error: ${e.message}")
            }
        }
    }

    fun shareToCommunity(recipe: Recipe) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isUploading = true,
                    uploadProgress = "Preparing to upload..."
                )

                var imageUrl = ""

                if (!recipe.imageUri.isNullOrEmpty()) {
                    val imageFile = File(recipe.imageUri)
                    if (imageFile.exists()) {
                        _uiState.value = _uiState.value.copy(
                            uploadProgress = "Uploading image..."
                        )

                        imageUrl = ImgurUploader.uploadImage(imageFile) ?: ""

                        if (imageUrl.isEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                uploadProgress = "Image upload failed, sharing without image"
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                uploadProgress = "Image uploaded successfully!"
                            )
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    uploadProgress = "Saving to community..."
                )

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
                    timestamp = System.currentTimeMillis(),
                    dietaryTags = recipe.dietaryTags ?: ""
                )

                kotlinx.coroutines.withTimeout(30000) {
                    firestore.collection("community_recipes")
                        .add(communityRecipe)
                        .await()
                }

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = "",
                    message = "Recipe shared to community!"
                )

            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = "",
                    message = "Upload timed out"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = "",
                    message = "Error: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    viewModel: SavedViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Recipe?>(null) }
    var showShareDialog by remember { mutableStateOf<Recipe?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    showDeleteDialog?.let { recipe ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Recipe") },
            text = { Text("Are you sure you want to delete '${recipe.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecipe(recipe)
                    showDeleteDialog = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showShareDialog?.let { recipe ->
        AlertDialog(
            onDismissRequest = { if (!uiState.isUploading) showShareDialog = null },
            title = { Text("Share to Community") },
            text = {
                Column {
                    if (uiState.isUploading) {
                        Text(uiState.uploadProgress)
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("Share '${recipe.title}' with the community?")
                    }
                }
            },
            confirmButton = {
                if (!uiState.isUploading) {
                    TextButton(onClick = {
                        viewModel.shareToCommunity(recipe)
                    }) {
                        Text("Share")
                    }
                }
            },
            dismissButton = {
                if (!uiState.isUploading) {
                    TextButton(onClick = { showShareDialog = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    LaunchedEffect(uiState.isUploading) {
        if (!uiState.isUploading && showShareDialog != null && uiState.message?.contains("shared") == true) {
            showShareDialog = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Saved Recipes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (uiState.recipes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved recipes yet.\nAdd some recipes to see them here!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.recipes, key = { it.id }) { recipe ->
                    SavedRecipeCard(
                        recipe = recipe,
                        isExpanded = uiState.expandedRecipeId == recipe.id,
                        onExpandClick = { viewModel.toggleExpanded(recipe.id) },
                        onDeleteClick = { showDeleteDialog = recipe },
                        onShareClick = { showShareDialog = recipe }
                    )
                }
            }
        }
    }
}

@Composable
fun SavedRecipeCard(
    recipe: Recipe,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (!recipe.imageUri.isNullOrEmpty()) {
                    val isUrl = recipe.imageUri.startsWith("http://") || recipe.imageUri.startsWith("https://")

                    if (isUrl) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(recipe.imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = recipe.title,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val imageFile = File(recipe.imageUri)
                        if (imageFile.exists()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageFile)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = recipe.title,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = recipe.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }

                IconButton(onClick = onExpandClick) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!recipe.imageUri.isNullOrEmpty()) {
                        val isUrl = recipe.imageUri.startsWith("http://") || recipe.imageUri.startsWith("https://")

                        if (isUrl) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(recipe.imageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = recipe.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            val imageFile = File(recipe.imageUri)
                            if (imageFile.exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imageFile)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = recipe.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    if (recipe.prepTime != null) {
                        Text("Prep Time: ${recipe.prepTime}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (recipe.servings != null) {
                        Text("Servings: ${recipe.servings}", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (recipe.ingredients != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Ingredients:", fontWeight = FontWeight.Bold)
                        Text(recipe.ingredients.replace(",", "\n• "))
                    }

                    if (recipe.instructions != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Instructions:", fontWeight = FontWeight.Bold)
                        Text(recipe.instructions)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onShareClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }

                        Button(
                            onClick = onDeleteClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}