package com.example.myapplication

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class HomeUiState(
    val recipes: List<CommunityRecipe> = emptyList(),
    val filteredRecipes: List<CommunityRecipe> = emptyList(),
    val searchQuery: String = "",
    val dietaryPreference: String = "None",
    val selectedRecipe: CommunityRecipe? = null,
    val message: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val preferencesManager = PreferencesManager(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadCommunityRecipes()
        loadUserPreference()
    }

    private fun loadUserPreference() {
        viewModelScope.launch {
            preferencesManager.dietaryPreference.collect { preference ->
                _uiState.value = _uiState.value.copy(dietaryPreference = preference)
                filterRecipes(_uiState.value.searchQuery)
            }
        }
    }

    private fun loadCommunityRecipes() {
        firestore.collection("community_recipes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = _uiState.value.copy(message = "Error loading recipes")
                    return@addSnapshotListener
                }

                val recipes = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CommunityRecipe::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(recipes = recipes)
                filterRecipes(_uiState.value.searchQuery)
            }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterRecipes(query)
    }

    private fun filterRecipes(query: String) {
        var filtered = if (query.isEmpty()) {
            _uiState.value.recipes
        } else {
            _uiState.value.recipes.filter { recipe ->
                recipe.title.contains(query, ignoreCase = true) ||
                        recipe.description.contains(query, ignoreCase = true) ||
                        recipe.ingredients.contains(query, ignoreCase = true) ||
                        recipe.authorEmail.contains(query, ignoreCase = true)
            }
        }

        val preference = _uiState.value.dietaryPreference
        if (preference != "None" && preference.isNotEmpty()) {
            filtered = filtered.filter { recipe ->
                val recipeTags = recipe.dietaryTags ?: ""
                recipeTags.contains(preference, ignoreCase = true)
            }
        }

        _uiState.value = _uiState.value.copy(filteredRecipes = filtered)
    }

    fun selectRecipe(recipe: CommunityRecipe) {
        _uiState.value = _uiState.value.copy(selectedRecipe = recipe)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedRecipe = null)
    }

    fun saveToMyRecipes(communityRecipe: CommunityRecipe) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.value = _uiState.value.copy(message = "Please log in to save recipes")
            return
        }

        viewModelScope.launch {
            try {
                val recipeData = hashMapOf(
                    "title" to communityRecipe.title,
                    "description" to communityRecipe.description,
                    "imageUri" to communityRecipe.imageUrl,
                    "ingredients" to communityRecipe.ingredients,
                    "instructions" to communityRecipe.instructions,
                    "prepTime" to communityRecipe.prepTime,
                    "servings" to communityRecipe.servings,
                    "timestamp" to System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(userId)
                    .collection("saved_recipes")
                    .add(recipeData)
                    .await()

                _uiState.value = _uiState.value.copy(message = "Recipe saved!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error: ${e.message}")
            }
        }
    }

    fun deleteFromCommunity(recipe: CommunityRecipe) {
        viewModelScope.launch {
            try {
                firestore.collection("community_recipes")
                    .document(recipe.id)
                    .delete()
                    .await()

                if (recipe.imageUrl.isNotEmpty()) {
                    try {
                        val imageRef = storage.getReferenceFromUrl(recipe.imageUrl)
                        imageRef.delete().await()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _uiState.value = _uiState.value.copy(message = "Post deleted successfully!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProfile: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val auth = FirebaseAuth.getInstance()
    val currentUserEmail = auth.currentUser?.email
    var showDeleteDialog by remember { mutableStateOf<CommunityRecipe?>(null) }

    // Show toast messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { recipe ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Post") },
            text = { Text("Delete '${recipe.title}' from community? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFromCommunity(recipe)
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

    // Recipe details dialog
    uiState.selectedRecipe?.let { recipe ->
        RecipeDetailsDialog(
            recipe = recipe,
            onDismiss = { viewModel.clearSelection() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Recipes") },
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
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search recipes...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                singleLine = true
            )

            // Recipe list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.filteredRecipes) { recipe ->
                    CommunityRecipeCard(
                        recipe = recipe,
                        currentUserEmail = currentUserEmail,
                        onViewDetails = { viewModel.selectRecipe(recipe) },
                        onSave = { viewModel.saveToMyRecipes(recipe) },
                        onDelete = { showDeleteDialog = recipe }
                    )
                }
            }
        }
    }
}

@Composable
fun CommunityRecipeCard(
    recipe: CommunityRecipe,
    currentUserEmail: String?,
    onViewDetails: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Image
            if (recipe.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(recipe.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = recipe.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "By: ${recipe.authorEmail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (recipe.prepTime.isNotEmpty()) {
                        Text(
                            text = "⏱ ${recipe.prepTime}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (recipe.servings.isNotEmpty()) {
                        Text(
                            text = "🍽 ${recipe.servings}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (recipe.dietaryTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🏷 ${recipe.dietaryTags}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onViewDetails,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("View Details")
                    }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }

                    if (recipe.authorEmail == currentUserEmail) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeDetailsDialog(
    recipe: CommunityRecipe,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text("By: ${recipe.authorEmail}\n", fontWeight = FontWeight.Bold)
                Text("${recipe.description}\n")
                if (recipe.prepTime.isNotEmpty()) Text("Prep Time: ${recipe.prepTime}\n")
                if (recipe.servings.isNotEmpty()) Text("Servings: ${recipe.servings}\n")
                if (recipe.ingredients.isNotEmpty()) {
                    Text("\nIngredients:", fontWeight = FontWeight.Bold)
                    Text(recipe.ingredients.replace(",", "\n• ") + "\n")
                }
                if (recipe.instructions.isNotEmpty()) {
                    Text("\nInstructions:", fontWeight = FontWeight.Bold)
                    Text(recipe.instructions)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}