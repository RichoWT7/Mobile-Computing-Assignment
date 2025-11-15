package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class Main : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var profileImageToolbar: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferencesManager = PreferencesManager(this)
        profileImageToolbar = findViewById(R.id.profile_image_toolbar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Load initial screen
        loadFragment(HomeFragment())

        // Toolbar profile picture click — open Profile screen (with backstack)
        profileImageToolbar.setOnClickListener {
            loadFragmentWithBackStack(ProfileFragment())
            bottomNav.selectedItemId = -1 // remove highlight from bottom nav
        }

        // Bottom Navigation handling
        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_saved -> SavedFragment()
                R.id.nav_add -> AddFragment()
                R.id.nav_calendar -> CalendarFragment()
                else -> HomeFragment()
            }
            loadFragment(fragment)
            true
        }

        // Load toolbar profile picture
        loadProfilePicture()
    }

    override fun onResume() {
        super.onResume()
        loadProfilePicture()
    }

    private fun loadProfilePicture() {
        lifecycleScope.launch {
            val savedProfilePic = preferencesManager.profilePicture.first()

            val file = File(savedProfilePic)
            if (savedProfilePic.isNotEmpty() && file.exists()) {
                profileImageToolbar.load(file) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.ic_person)
                    error(R.drawable.ic_person)
                }
            } else {
                profileImageToolbar.load(R.drawable.ic_person) {
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    fun refreshProfilePicture() {
        loadProfilePicture()
    }

    // Normal navigation (no backstack)
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // Navigation WITH backstack (for Profile from toolbar)
    private fun loadFragmentWithBackStack(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
