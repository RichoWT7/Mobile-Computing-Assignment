package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapter.MealPlanAdapter
import com.example.myapplication.adapter.RecipeSelectionAdapter
import com.example.myapplication.adapter.WeeklyOverviewAdapter
import com.example.myapplication.data.MealPlan
import com.example.myapplication.data.Recipe
import com.example.myapplication.viewmodel.MealPlanViewModel
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private val mealPlanViewModel: MealPlanViewModel by viewModels()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var mealPlanAdapter: MealPlanAdapter
    private lateinit var weeklyOverviewAdapter: WeeklyOverviewAdapter
    private lateinit var dateTabLayout: TabLayout
    private lateinit var shoppingListContainer: LinearLayout
    private lateinit var generateShoppingListButton: Button
    private var currentDate: String = ""
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val weekDates = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        dateTabLayout = view.findViewById(R.id.date_tab_layout)
        val mealPlanRecyclerView = view.findViewById<RecyclerView>(R.id.meal_plan_recycler_view)
        val weeklyOverviewRecyclerView = view.findViewById<RecyclerView>(R.id.weekly_overview_recycler_view)
        shoppingListContainer = view.findViewById(R.id.shopping_list_container)
        generateShoppingListButton = view.findViewById(R.id.generate_shopping_list_button)

        mealPlanRecyclerView.layoutManager = LinearLayoutManager(context)
        weeklyOverviewRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        // Set current date
        currentDate = dateFormat.format(Date())

        // Setup date tabs (show 7 days)
        setupDateTabs()

        // Setup meal plan adapter
        mealPlanAdapter = MealPlanAdapter(
            mealPlans = emptyList(),
            onAddClick = { mealType ->
                showRecipeSelectionDialog(currentDate, mealType)
            },
            onDeleteClick = { mealPlan ->
                mealPlanViewModel.delete(mealPlan)
            }
        )
        mealPlanRecyclerView.adapter = mealPlanAdapter

        // Setup weekly overview adapter
        weeklyOverviewAdapter = WeeklyOverviewAdapter(
            weeklyData = emptyList(),
            onDateClick = { date ->
                selectDate(date)
            }
        )
        weeklyOverviewRecyclerView.adapter = weeklyOverviewAdapter

        // Load meal plans for current date
        loadMealPlansForDate(currentDate)
        loadWeeklyOverview()

        // Handle date tab selection
        dateTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val selectedDate = it.tag as String
                    currentDate = selectedDate
                    loadMealPlansForDate(selectedDate)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Shopping list button
        generateShoppingListButton.setOnClickListener {
            generateShoppingList()
        }

        return view
    }

    private fun setupDateTabs() {
        val calendar = Calendar.getInstance()
        weekDates.clear()

        for (i in 0..6) {
            val date = calendar.time
            val dateString = dateFormat.format(date)
            val displayString = displayDateFormat.format(date)

            weekDates.add(dateString)

            val tab = dateTabLayout.newTab()
            tab.text = displayString
            tab.tag = dateString
            dateTabLayout.addTab(tab)

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Select first tab
        dateTabLayout.getTabAt(0)?.select()
    }

    private fun selectDate(date: String) {
        currentDate = date
        val tabIndex = weekDates.indexOf(date)
        if (tabIndex >= 0) {
            dateTabLayout.getTabAt(tabIndex)?.select()
        }
        loadMealPlansForDate(date)

        // Update weekly overview to highlight selected date
        weeklyOverviewAdapter.updateSelectedDate(date)
    }

    private fun loadMealPlansForDate(date: String) {
        val userId = auth.currentUser?.uid ?: return

        mealPlanViewModel.getMealPlansForDate(userId, date).observe(viewLifecycleOwner) { mealPlans ->
            mealPlanAdapter.updateMealPlans(mealPlans, date)
        }
    }

    private fun loadWeeklyOverview() {
        val userId = auth.currentUser?.uid ?: return

        mealPlanViewModel.getAllMealPlans(userId).observe(viewLifecycleOwner) { allMealPlans ->
            // Filter to current week and group by date
            val weeklyData = weekDates.map { date ->
                val mealsForDate = allMealPlans.filter { it.date == date }
                WeeklyOverviewAdapter.DayData(
                    date = date,
                    displayDate = displayDateFormat.format(dateFormat.parse(date)!!),
                    mealCount = mealsForDate.size,
                    hasBreakfast = mealsForDate.any { it.mealType == "Breakfast" },
                    hasLunch = mealsForDate.any { it.mealType == "Lunch" },
                    hasDinner = mealsForDate.any { it.mealType == "Dinner" }
                )
            }
            weeklyOverviewAdapter.updateWeeklyData(weeklyData, currentDate)
        }
    }

    private fun generateShoppingList() {
        val userId = auth.currentUser?.uid ?: return

        // Remove previous observers to avoid multiple calls
        mealPlanViewModel.getAllMealPlans(userId).removeObservers(viewLifecycleOwner)

        mealPlanViewModel.getAllMealPlans(userId).observe(viewLifecycleOwner) { allMealPlans ->
            // Remove observer after first call
            mealPlanViewModel.getAllMealPlans(userId).removeObservers(viewLifecycleOwner)

            if (allMealPlans.isEmpty()) {
                Toast.makeText(context, "No meals planned. Add some meals first!", Toast.LENGTH_LONG).show()
                return@observe
            }

            // Filter to current week
            val weekMealPlans = allMealPlans.filter { it.date in weekDates }

            if (weekMealPlans.isEmpty()) {
                Toast.makeText(context, "No meals planned for this week. Try adding meals to the calendar!", Toast.LENGTH_LONG).show()
                return@observe
            }

            // Collect all ingredients
            val ingredientsMap = mutableMapOf<String, Int>()

            weekMealPlans.forEach { mealPlan ->
                // Split by comma and also by newline (in case ingredients have line breaks)
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
                Toast.makeText(context, "No ingredients found in your planned meals", Toast.LENGTH_SHORT).show()
                return@observe
            }

            // Display shopping list
            showShoppingListDialog(ingredientsMap)
        }
    }

    private fun showShoppingListDialog(ingredientsMap: Map<String, Int>) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_shopping_list, null)

        val container = dialogView.findViewById<LinearLayout>(R.id.shopping_list_items_container)
        val shareButton = dialogView.findViewById<Button>(R.id.share_shopping_list_button)
        val clearButton = dialogView.findViewById<Button>(R.id.clear_shopping_list_button)

        // Load saved checkbox states
        val sharedPrefs = requireContext().getSharedPreferences("shopping_list", android.content.Context.MODE_PRIVATE)

        // Add checkboxes for each ingredient
        val sortedIngredients = ingredientsMap.entries.sortedBy { it.key }
        val checkBoxes = mutableListOf<CheckBox>()

        sortedIngredients.forEach { (ingredient, count) ->
            val displayText = if (count > 1) "$ingredient (×$count)" else ingredient
            val checkBox = CheckBox(requireContext()).apply {
                text = displayText
                textSize = 16f
                setPadding(16, 8, 16, 8)

                // Restore saved state
                isChecked = sharedPrefs.getBoolean(ingredient, false)

                // Save state when checked/unchecked
                setOnCheckedChangeListener { _, isChecked ->
                    sharedPrefs.edit().putBoolean(ingredient, isChecked).apply()
                }
            }
            checkBoxes.add(checkBox)
            container.addView(checkBox)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Shopping List for This Week")
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .create()

        shareButton.setOnClickListener {
            shareShoppingList(ingredientsMap)
        }

        clearButton.setOnClickListener {
            // Clear all checkboxes
            checkBoxes.forEach { checkBox ->
                checkBox.isChecked = false
            }
            // Clear saved preferences
            sortedIngredients.forEach { (ingredient, _) ->
                sharedPrefs.edit().remove(ingredient).apply()
            }
            Toast.makeText(context, "Shopping list cleared!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun shareShoppingList(ingredientsMap: Map<String, Int>) {
        val shoppingListText = buildString {
            append("🛒 Shopping List\n\n")
            ingredientsMap.entries.sortedBy { it.key }.forEach { (ingredient, count) ->
                append("☐ ")
                if (count > 1) {
                    append("$ingredient (×$count)\n")
                } else {
                    append("$ingredient\n")
                }
            }
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shoppingListText)
            type = "text/plain"
        }

        startActivity(Intent.createChooser(shareIntent, "Share Shopping List"))
    }

    private fun showRecipeSelectionDialog(date: String, mealType: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "Please log in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Load saved recipes from Firestore
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

                if (recipes.isEmpty()) {
                    Toast.makeText(context, "No saved recipes. Add some first!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Show dialog with recipe list
                val dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_recipe_selection, null)

                val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recipe_selection_recycler_view)
                recyclerView.layoutManager = LinearLayoutManager(context)

                val adapter = RecipeSelectionAdapter(recipes) { selectedRecipe ->
                    addMealPlan(date, mealType, selectedRecipe)
                }
                recyclerView.adapter = adapter

                AlertDialog.Builder(requireContext())
                    .setTitle("Select Recipe for $mealType")
                    .setView(dialogView)
                    .setNegativeButton("Cancel", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(context, "Error loading recipes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMealPlan(date: String, mealType: String, recipe: Recipe) {
        val userId = auth.currentUser?.uid ?: return

        val mealPlan = MealPlan(
            date = date,
            mealType = mealType,
            recipeTitle = recipe.title,
            recipeDescription = recipe.description,
            recipeImageUri = recipe.imageUri,
            recipeIngredients = recipe.ingredients,
            recipeInstructions = recipe.instructions,
            recipePrepTime = recipe.prepTime,
            recipeServings = recipe.servings,
            userId = userId
        )

        mealPlanViewModel.insert(mealPlan)
        Toast.makeText(context, "Added to $mealType", Toast.LENGTH_SHORT).show()

        // Refresh weekly overview
        loadWeeklyOverview()
    }
}