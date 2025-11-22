package com.example.myapplication

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.myapplication.data.Comment
import com.example.myapplication.data.CommunityRecipe
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// Updated HomeScreen UI State with Comments
data class HomeUiState(
    val recipes: List<CommunityRecipe> = emptyList(),
    val filteredRecipes: List<CommunityRecipe> = emptyList(),
    val searchQuery: String = "",
    val selectedDietaryFilter: String = "All",
    val expandedRecipeId: String? = null,
    val showComments: String? = null, // Recipe ID for which to show comments
    val comments: Map<String, List<Comment>> = emptyMap(), // Recipe ID -> Comments
    val newCommentText: String = "",
    val isLoading: Boolean = false,
    val message: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecipes()
    }

    fun loadRecipes() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val snapshot = firestore.collection("community_recipes")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val recipes = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CommunityRecipe::class.java)?.copy(id = doc.id)
                }

                _uiState.value = _uiState.value.copy(
                    recipes = recipes,
                    filteredRecipes = recipes,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Error loading recipes"
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun updateDietaryFilter(filter: String) {
        _uiState.value = _uiState.value.copy(selectedDietaryFilter = filter)
        applyFilters()
    }

    private fun applyFilters() {
        val query = _uiState.value.searchQuery.lowercase()
        val filter = _uiState.value.selectedDietaryFilter

        val filtered = _uiState.value.recipes.filter { recipe ->
            val matchesSearch = query.isEmpty() ||
                    recipe.title.lowercase().contains(query) ||
                    recipe.description.lowercase().contains(query)

            val matchesFilter = filter == "All" || recipe.dietaryTags?.contains(filter) == true

            matchesSearch && matchesFilter
        }

        _uiState.value = _uiState.value.copy(filteredRecipes = filtered)
    }

    fun toggleRecipeExpansion(recipeId: String) {
        _uiState.value = _uiState.value.copy(
            expandedRecipeId = if (_uiState.value.expandedRecipeId == recipeId) null else recipeId
        )
    }

    fun showComments(recipeId: String) {
        _uiState.value = _uiState.value.copy(showComments = recipeId)
        loadComments(recipeId)
    }

    fun hideComments() {
        _uiState.value = _uiState.value.copy(
            showComments = null,
            newCommentText = ""
        )
    }

    fun updateCommentText(text: String) {
        _uiState.value = _uiState.value.copy(newCommentText = text)
    }

    private fun loadComments(recipeId: String) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("community_recipes")
                    .document(recipeId)
                    .collection("comments")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val comments = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Comment::class.java)?.copy(id = doc.id)
                }

                val updatedCommentsMap = _uiState.value.comments.toMutableMap()
                updatedCommentsMap[recipeId] = comments

                _uiState.value = _uiState.value.copy(comments = updatedCommentsMap)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error loading comments")
            }
        }
    }

    fun addComment(recipeId: String) {
        val commentText = _uiState.value.newCommentText.trim()
        if (commentText.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "Comment cannot be empty")
            return
        }

        val user = auth.currentUser
        if (user == null) {
            _uiState.value = _uiState.value.copy(message = "Please log in to comment")
            return
        }

        viewModelScope.launch {
            try {
                val comment = Comment(
                    recipeId = recipeId,
                    authorEmail = user.email ?: "Anonymous",
                    authorName = user.email?.substringBefore("@") ?: "Anonymous",
                    commentText = commentText,
                    timestamp = System.currentTimeMillis()
                )

                firestore.collection("community_recipes")
                    .document(recipeId)
                    .collection("comments")
                    .add(comment)
                    .await()

                _uiState.value = _uiState.value.copy(
                    newCommentText = "",
                    message = "Comment added!"
                )

                loadComments(recipeId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error adding comment")
            }
        }
    }

    fun deleteComment(recipeId: String, commentId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("community_recipes")
                    .document(recipeId)
                    .collection("comments")
                    .document(commentId)
                    .delete()
                    .await()

                _uiState.value = _uiState.value.copy(message = "Comment deleted")

                // Reload comments
                loadComments(recipeId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error deleting comment")
            }
        }
    }

    fun deleteRecipe(recipeId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("community_recipes")
                    .document(recipeId)
                    .delete()
                    .await()

                _uiState.value = _uiState.value.copy(message = "Recipe deleted")
                loadRecipes()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error deleting recipe")
            }
        }
    }

    fun saveRecipeToMyRecipes(recipe: CommunityRecipe) {
        println("DEBUG: saveRecipeToMyRecipes called for: ${recipe.title}")
        val userId = auth.currentUser?.uid
        println("DEBUG: User ID: $userId")

        if (userId == null) {
            println("DEBUG: User not logged in")
            _uiState.value = _uiState.value.copy(message = "Please log in to save recipes")
            return
        }

        viewModelScope.launch {
            try {
                println("DEBUG: Starting save to Firestore")
                val savedRecipe = hashMapOf(
                    "title" to recipe.title,
                    "description" to recipe.description,
                    "imageUri" to recipe.imageUrl,
                    "prepTime" to recipe.prepTime,
                    "servings" to recipe.servings,
                    "ingredients" to recipe.ingredients,
                    "instructions" to recipe.instructions,
                    "dietaryTags" to (recipe.dietaryTags ?: ""),
                    "timestamp" to System.currentTimeMillis(),
                    "isFromCommunity" to true
                )

                firestore.collection("users")
                    .document(userId)
                    .collection("saved_recipes")
                    .add(savedRecipe)
                    .await()

                println("DEBUG: Save successful, setting message")
                _uiState.value = _uiState.value.copy(message = "✅ Recipe saved to your Saved tab!")
                println("DEBUG: Message set to: ${_uiState.value.message}")
            } catch (e: Exception) {
                println("DEBUG: Error saving: ${e.message}")
                _uiState.value = _uiState.value.copy(message = "❌ Error saving recipe: ${e.message}")
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
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            println("DEBUG: Showing snackbar with message: $message")
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (message.lowercase().contains("saved")) "OK" else null,
                duration = SnackbarDuration.Long
            )
            viewModel.clearMessage()
        }
    }

    // Comments Dialog
    uiState.showComments?.let { recipeId ->
        CommentsDialog(
            recipeId = recipeId,
            comments = uiState.comments[recipeId] ?: emptyList(),
            newCommentText = uiState.newCommentText,
            currentUserEmail = FirebaseAuth.getInstance().currentUser?.email,
            onCommentTextChange = { viewModel.updateCommentText(it) },
            onAddComment = { viewModel.addComment(recipeId) },
            onDeleteComment = { commentId -> viewModel.deleteComment(recipeId, commentId) },
            onDismiss = { viewModel.hideComments() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search recipes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("All", "Vegetarian", "Vegan", "Gluten-Free", "Keto", "Paleo")) { filter ->
                    FilterChip(
                        selected = uiState.selectedDietaryFilter == filter,
                        onClick = { viewModel.updateDietaryFilter(filter) },
                        label = { Text(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredRecipes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recipes found")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredRecipes) { recipe ->
                        CommunityRecipeCard(
                            recipe = recipe,
                            isExpanded = uiState.expandedRecipeId == recipe.id,
                            currentUserEmail = FirebaseAuth.getInstance().currentUser?.email,
                            onExpandClick = { viewModel.toggleRecipeExpansion(recipe.id) },
                            onDeleteClick = { viewModel.deleteRecipe(recipe.id) },
                            onCommentsClick = { viewModel.showComments(recipe.id) },
                            onSaveClick = { viewModel.saveRecipeToMyRecipes(recipe) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityRecipeCard(
    recipe: CommunityRecipe,
    isExpanded: Boolean,
    currentUserEmail: String?,
    onExpandClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandClick)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "by ${recipe.authorName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onExpandClick) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand"
                    )
                }
            }

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
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = recipe.description,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )

            if (isExpanded) {
                Divider()

                Column(modifier = Modifier.padding(16.dp)) {
                    if (recipe.prepTime.isNotEmpty()) {
                        Text("⏱ Prep Time: ${recipe.prepTime}")
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (recipe.servings.isNotEmpty()) {
                        Text("🍽 Servings: ${recipe.servings}")
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (recipe.ingredients.isNotEmpty()) {
                        Text("Ingredients:", fontWeight = FontWeight.Bold)
                        Text(recipe.ingredients.replace(",", "\n• "))
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (recipe.instructions.isNotEmpty()) {
                        Text("Instructions:", fontWeight = FontWeight.Bold)
                        Text(recipe.instructions)
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSaveClick) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }

                TextButton(onClick = onCommentsClick) {
                    Icon(Icons.Default.Comment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Comments")
                }

                if (currentUserEmail == recipe.authorEmail) {
                    TextButton(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun CommentsDialog(
    recipeId: String,
    comments: List<Comment>,
    newCommentText: String,
    currentUserEmail: String?,
    onCommentTextChange: (String) -> Unit,
    onAddComment: () -> Unit,
    onDeleteComment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {

                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = onCommentTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write a comment...") },
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onAddComment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Post Comment")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (comments.isEmpty()) {
                    Text(
                        "No comments yet. Be the first to comment!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(comments) { comment ->
                            CommentItem(
                                comment = comment,
                                currentUserEmail = currentUserEmail,
                                onDelete = { onDeleteComment(comment.id) }
                            )
                        }
                    }
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

@Composable
fun CommentItem(
    comment: Comment,
    currentUserEmail: String?,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // User Avatar
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = comment.authorName.first().uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = comment.authorName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatTimestamp(comment.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (currentUserEmail == comment.authorEmail) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = comment.commentText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}