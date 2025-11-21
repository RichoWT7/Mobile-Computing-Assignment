package com.example.myapplication

import android.app.Application
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myapplication.data.MealPlan
import com.example.myapplication.data.Recipe
import com.example.myapplication.repository.MealPlanRepository
import com.example.myapplication.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Data classes
data class DayData(
    val date: String,
    val displayDate: String,
    val mealCount: Int,
    val hasBreakfast: Boolean,
    val hasLunch: Boolean,
    val hasDinner: Boolean
)

data class CalendarUiState(
    val currentDate: String = "",
    val weekDates: List<String> = emptyList(),
    val weekOffset: Int = 0,
    val weekLabel: String = "This Week",
    val expandedMealType: String? = null,
    val showRecipeSelection: Boolean = false,
    val recipeSelectionMealType: String = "",
    val availableRecipes: List<Recipe> = emptyList(),
    val showShoppingList: Boolean = false,
    val shoppingListItems: Map<String, ShoppingItem> = emptyMap(),
    val message: String? = null
)

data class ShoppingItem(
    val ingredient: String,
    val count: Int,
    val isChecked: Boolean = false
)

// ViewModel
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val context = application
    private val repository: MealPlanRepository

    init {
        val mealPlanDao = AppDatabase.getDatabase(application).mealPlanDao()
        repository = MealPlanRepository(mealPlanDao)
    }

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var _allMealPlans = MutableStateFlow<List<MealPlan>>(emptyList())

    init {
        setupWeek()
    }

    fun getMealPlansForDate(date: String): LiveData<List<MealPlan>> {
        val userId = auth.currentUser?.uid ?: ""
        return repository.getMealPlansForDate(userId, date)
    }

    fun getAllMealPlans(): LiveData<List<MealPlan>> {
        val userId = auth.currentUser?.uid ?: ""
        return repository.getAllMealPlans(userId)
    }

    private fun setupWeek() {
        val calendar = Calendar.getInstance()
        val currentOffset = _uiState.value.weekOffset
        calendar.add(Calendar.WEEK_OF_YEAR, currentOffset)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val weekDates = mutableListOf<String>()

        val tempCal = calendar.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_MONTH, -3)
        for (i in 0..2) {
            weekDates.add(dateFormat.format(tempCal.time))
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        for (i in 0..6) {
            val date = calendar.time
            weekDates.add(dateFormat.format(date))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        for (i in 0..2) {
            weekDates.add(dateFormat.format(calendar.time))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val currentDate = _uiState.value.currentDate.ifEmpty {
            weekDates[3]
        }

        _uiState.value = _uiState.value.copy(
            weekDates = weekDates,
            currentDate = currentDate,
            weekLabel = getWeekLabel(currentOffset)
        )
    }

    private fun getWeekLabel(offset: Int): String {
        return when (offset) {
            0 -> "This Week"
            -1 -> "Last Week"
            1 -> "Next Week"
            else -> if (offset > 0) "$offset Weeks Ahead" else "${-offset} Weeks Ago"
        }
    }

    fun selectDate(date: String) {
        _uiState.value = _uiState.value.copy(currentDate = date)
    }

    fun previousWeek() {
        _uiState.value = _uiState.value.copy(weekOffset = _uiState.value.weekOffset - 1)
        setupWeek()
    }

    fun nextWeek() {
        _uiState.value = _uiState.value.copy(weekOffset = _uiState.value.weekOffset + 1)
        setupWeek()
    }

    fun updateWeeklyData(allPlans: List<MealPlan>): List<DayData> {
        _allMealPlans.value = allPlans
        return _uiState.value.weekDates.map { date ->
            val mealsForDate = allPlans.filter { it.date == date }
            DayData(
                date = date,
                displayDate = try {
                    displayDateFormat.format(dateFormat.parse(date)!!)
                } catch (e: Exception) {
                    date
                },
                mealCount = mealsForDate.size,
                hasBreakfast = mealsForDate.any { it.mealType == "Breakfast" },
                hasLunch = mealsForDate.any { it.mealType == "Lunch" },
                hasDinner = mealsForDate.any { it.mealType == "Dinner" }
            )
        }
    }

    fun toggleMealExpansion(mealType: String) {
        _uiState.value = _uiState.value.copy(
            expandedMealType = if (_uiState.value.expandedMealType == mealType) null else mealType
        )
    }

    fun showRecipeSelection(mealType: String) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch

                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("saved_recipes")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val recipes = snapshot.documents.mapNotNull { doc ->
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
                            firestoreId = doc.id
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                _uiState.value = _uiState.value.copy(
                    showRecipeSelection = true,
                    recipeSelectionMealType = mealType,
                    availableRecipes = recipes
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error loading recipes")
            }
        }
    }

    fun closeRecipeSelection() {
        _uiState.value = _uiState.value.copy(showRecipeSelection = false)
    }

    fun addMealPlan(recipe: Recipe) {
        val userId = auth.currentUser?.uid ?: return
        val mealType = _uiState.value.recipeSelectionMealType
        val date = _uiState.value.currentDate

        viewModelScope.launch {
            try {
                val mealPlan = MealPlan(
                    id = 0, // Auto-generate
                    date = date,
                    mealType = mealType,
                    recipeTitle = recipe.title,
                    recipeDescription = recipe.description,
                    recipeImageUri = recipe.imageUri ?: "",
                    recipeIngredients = recipe.ingredients,
                    recipeInstructions = recipe.instructions,
                    recipePrepTime = recipe.prepTime,
                    recipeServings = recipe.servings,
                    userId = userId
                )

                repository.insert(mealPlan)

                _uiState.value = _uiState.value.copy(
                    showRecipeSelection = false,
                    message = "Added to $mealType"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error: ${e.message}")
            }
        }
    }

    fun deleteMealPlan(mealPlan: MealPlan) {
        viewModelScope.launch {
            try {
                repository.delete(mealPlan)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Error deleting")
            }
        }
    }

    fun generateShoppingList() {
        val weekMealPlans = _allMealPlans.value.filter {
            it.date in _uiState.value.weekDates
        }

        if (weekMealPlans.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                message = "No meals planned for this week"
            )
            return
        }

        val ingredientsMap = mutableMapOf<String, Int>()

        weekMealPlans.forEach { mealPlan ->
            val ingredients = mealPlan.recipeIngredients
                ?.split(",", "\n")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            ingredients.forEach { ingredient ->
                if (ingredient.isNotEmpty()) {
                    ingredientsMap[ingredient] = ingredientsMap.getOrDefault(ingredient, 0) + 1
                }
            }
        }

        if (ingredientsMap.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                message = "No ingredients found in planned meals"
            )
            return
        }

        val prefs = context.getSharedPreferences("shopping_list", Context.MODE_PRIVATE)
        val shoppingItems = ingredientsMap.map { (ingredient, count) ->
            ingredient to ShoppingItem(
                ingredient = ingredient,
                count = count,
                isChecked = prefs.getBoolean(ingredient, false)
            )
        }.toMap()

        _uiState.value = _uiState.value.copy(
            showShoppingList = true,
            shoppingListItems = shoppingItems
        )
    }

    fun toggleShoppingItem(ingredient: String) {
        val currentItems = _uiState.value.shoppingListItems.toMutableMap()
        val item = currentItems[ingredient] ?: return

        currentItems[ingredient] = item.copy(isChecked = !item.isChecked)

        val prefs = context.getSharedPreferences("shopping_list", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ingredient, !item.isChecked).apply()

        _uiState.value = _uiState.value.copy(shoppingListItems = currentItems)
    }

    fun clearShoppingList() {
        val prefs = context.getSharedPreferences("shopping_list", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        _uiState.value.shoppingListItems.keys.forEach { ingredient ->
            editor.remove(ingredient)
        }
        editor.apply()

        val clearedItems = _uiState.value.shoppingListItems.mapValues { (_, item) ->
            item.copy(isChecked = false)
        }

        _uiState.value = _uiState.value.copy(
            shoppingListItems = clearedItems,
            message = "Shopping list cleared!"
        )
    }

    fun shareShoppingList() {
        val shoppingListText = buildString {
            append("🛒 Shopping List\n\n")
            _uiState.value.shoppingListItems.entries.sortedBy { it.key }.forEach { (ingredient, item) ->
                append("☐ ")
                if (item.count > 1) {
                    append("$ingredient (×${item.count})\n")
                } else {
                    append("$ingredient\n")
                }
            }
        }

        _uiState.value = _uiState.value.copy(message = "Share: $shoppingListText")
    }

    fun closeShoppingList() {
        _uiState.value = _uiState.value.copy(showShoppingList = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val currentMealPlans by viewModel.getMealPlansForDate(uiState.currentDate).observeAsState(emptyList())
    val allMealPlans by viewModel.getAllMealPlans().observeAsState(emptyList())

    val weeklyData = remember(allMealPlans, uiState.weekDates) {
        viewModel.updateWeeklyData(allMealPlans)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            if (!message.startsWith("Share:")) {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val shareText = message.removePrefix("Share: ")
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Shopping List"))
            }
            viewModel.clearMessage()
        }
    }

    if (uiState.showRecipeSelection) {
        RecipeSelectionDialog(
            recipes = uiState.availableRecipes,
            mealType = uiState.recipeSelectionMealType,
            onSelectRecipe = { recipe -> viewModel.addMealPlan(recipe) },
            onDismiss = { viewModel.closeRecipeSelection() }
        )
    }

    if (uiState.showShoppingList) {
        ShoppingListDialog(
            items = uiState.shoppingListItems,
            onToggleItem = { ingredient -> viewModel.toggleShoppingItem(ingredient) },
            onClear = { viewModel.clearShoppingList() },
            onShare = { viewModel.shareShoppingList() },
            onDismiss = { viewModel.closeShoppingList() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meal Calendar") },
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
            WeekNavigationBar(
                weekLabel = uiState.weekLabel,
                onPreviousWeek = { viewModel.previousWeek() },
                onNextWeek = { viewModel.nextWeek() }
            )

            DateTabRow(
                dates = uiState.weekDates,
                currentDate = uiState.currentDate,
                onSelectDate = { date -> viewModel.selectDate(date) }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(listOf("Breakfast", "Lunch", "Dinner")) { mealType ->
                    val mealPlan = currentMealPlans.find { it.mealType == mealType }
                    MealPlanCard(
                        mealType = mealType,
                        mealPlan = mealPlan,
                        isExpanded = uiState.expandedMealType == mealType,
                        onExpandClick = { viewModel.toggleMealExpansion(mealType) },
                        onAddClick = { viewModel.showRecipeSelection(mealType) },
                        onDeleteClick = { mealPlan?.let { viewModel.deleteMealPlan(it) } }
                    )
                }
            }

            WeeklyOverview(
                weeklyData = weeklyData,
                currentDate = uiState.currentDate,
                onDateClick = { date -> viewModel.selectDate(date) }
            )

            Button(
                onClick = { viewModel.generateShoppingList() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Shopping List")
            }
        }
    }
}

@Composable
fun WeekNavigationBar(
    weekLabel: String,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousWeek) {
            Icon(Icons.Default.ArrowBack, "Previous Week")
        }

        Text(
            text = weekLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = onNextWeek) {
            Icon(Icons.Default.ArrowForward, "Next Week")
        }
    }
}

@Composable
fun WeeklyOverview(
    weeklyData: List<DayData>,
    currentDate: String,
    onDateClick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(weeklyData) { dayData ->
            WeekDayCard(
                dayData = dayData,
                isSelected = dayData.date == currentDate,
                onClick = { onDateClick(dayData.date) }
            )
        }
    }
}

@Composable
fun WeekDayCard(
    dayData: DayData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(70.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dayData.displayDate,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "${dayData.mealCount} meals",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (dayData.hasBreakfast) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                if (dayData.hasLunch) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                if (dayData.hasDinner) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }
    }
}

@Composable
fun DateTabRow(
    dates: List<String>,
    currentDate: String,
    onSelectDate: (String) -> Unit
) {
    val displayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    ScrollableTabRow(
        selectedTabIndex = dates.indexOf(currentDate).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth()
    ) {
        dates.forEach { date ->
            val displayDate = try {
                displayFormat.format(dateFormat.parse(date)!!)
            } catch (e: Exception) {
                date
            }

            Tab(
                selected = date == currentDate,
                onClick = { onSelectDate(date) },
                text = { Text(displayDate) }
            )
        }
    }
}

@Composable
fun MealPlanCard(
    mealType: String,
    mealPlan: MealPlan?,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (mealPlan != null) onExpandClick() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$mealType:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (mealPlan != null) {
                    IconButton(onClick = onExpandClick) {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand"
                        )
                    }
                } else {
                    IconButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add meal")
                    }
                }
            }

            if (mealPlan != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Image
                    if (!mealPlan.recipeImageUri.isNullOrEmpty()) {
                        val imageFile = File(mealPlan.recipeImageUri)
                        if (imageFile.exists()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageFile)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = mealPlan.recipeTitle,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mealPlan.recipeTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (mealPlan.recipePrepTime?.isNotEmpty() == true || mealPlan.recipeServings?.isNotEmpty() == true) {
                            Text(
                                text = buildString {
                                    if (mealPlan.recipePrepTime?.isNotEmpty() == true) {
                                        append("⏱ ${mealPlan.recipePrepTime}")
                                    }
                                    if (mealPlan.recipeServings?.isNotEmpty() == true) {
                                        if (isNotEmpty()) append(" • ")
                                        append("🍽 ${mealPlan.recipeServings}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isExpanded) {
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!mealPlan.recipeImageUri.isNullOrEmpty()) {
                            val imageFile = File(mealPlan.recipeImageUri)
                            if (imageFile.exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imageFile)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = mealPlan.recipeTitle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        Text(
                            text = mealPlan.recipeDescription,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (mealPlan.recipePrepTime?.isNotEmpty() == true) {
                            Text("Prep Time: ${mealPlan.recipePrepTime}")
                        }
                        if (mealPlan.recipeServings?.isNotEmpty() == true) {
                            Text("Servings: ${mealPlan.recipeServings}")
                        }

                        if (mealPlan.recipeIngredients?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Ingredients:", fontWeight = FontWeight.Bold)
                            Text(mealPlan.recipeIngredients.replace(",", "\n• "))
                        }

                        if (mealPlan.recipeInstructions?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Instructions:", fontWeight = FontWeight.Bold)
                            Text(mealPlan.recipeInstructions)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onDeleteClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove from Plan")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No meal planned",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RecipeSelectionDialog(
    recipes: List<Recipe>,
    mealType: String,
    onSelectRecipe: (Recipe) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Recipe for $mealType") },
        text = {
            if (recipes.isEmpty()) {
                Text("No saved recipes. Add some recipes first!")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(recipes) { recipe ->
                        RecipeSelectionItem(
                            recipe = recipe,
                            onClick = { onSelectRecipe(recipe) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RecipeSelectionItem(
    recipe: Recipe,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!recipe.imageUri.isNullOrEmpty()) {
                val imageFile = File(recipe.imageUri)
                if (imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = recipe.title,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ShoppingListDialog(
    items: Map<String, ShoppingItem>,
    onToggleItem: (String) -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shopping List for This Week") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                if (items.isEmpty()) {
                    Text("No ingredients found in your planned meals")
                } else {
                    LazyColumn {
                        items(items.entries.sortedBy { it.key }.toList()) { (ingredient, item) ->
                            ShoppingListItemRow(
                                item = item,
                                onToggle = { onToggleItem(ingredient) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onShare,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }

                        Button(
                            onClick = onClear,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun ShoppingListItemRow(
    item: ShoppingItem,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggle() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (item.count > 1) {
                "${item.ingredient} (×${item.count})"
            } else {
                item.ingredient
            },
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.isChecked) {
                androidx.compose.ui.text.style.TextDecoration.LineThrough
            } else {
                androidx.compose.ui.text.style.TextDecoration.None
            }
        )
    }
}