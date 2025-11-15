package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var preferencesManager: PreferencesManager
    private var profileImageUri: Uri? = null
    private lateinit var profileImageView: ImageView

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            profileImageUri = it
            val savedPath = saveProfilePicture(it)
            lifecycleScope.launch {
                preferencesManager.saveProfilePicture(savedPath ?: "")
            }
            profileImageView.load(it) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }

            // Refresh toolbar profile picture
            (activity as? Main)?.refreshProfilePicture()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        auth = FirebaseAuth.getInstance()
        preferencesManager = PreferencesManager(requireContext())

        // Back button
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        backButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        profileImageView = view.findViewById(R.id.profile_image)
        val changeProfilePicButton = view.findViewById<Button>(R.id.change_profile_picture_button)
        val emailText = view.findViewById<TextView>(R.id.user_email)
        val dietarySpinner = view.findViewById<Spinner>(R.id.dietary_spinner)
        val saveButton = view.findViewById<Button>(R.id.save_preferences_button)
        val logoutButton = view.findViewById<Button>(R.id.logout_button)

        // Set up dietary preferences spinner
        val dietaryOptions = arrayOf("None", "Vegetarian", "Vegan", "Gluten-Free", "Keto", "Paleo")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, dietaryOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dietarySpinner.adapter = adapter

        // Load user data
        lifecycleScope.launch {
            val email = preferencesManager.userEmail.first()
            val savedPreference = preferencesManager.dietaryPreference.first()
            val savedProfilePic = preferencesManager.profilePicture.first()

            emailText.text = email.ifEmpty { "Not logged in" }
            val position = dietaryOptions.indexOf(savedPreference)
            if (position >= 0) {
                dietarySpinner.setSelection(position)
            }

            // Load profile picture
            if (savedProfilePic.isNotEmpty()) {
                val file = File(savedProfilePic)
                if (file.exists()) {
                    profileImageView.load(file) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                    }
                }
            }
        }

        changeProfilePicButton.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        saveButton.setOnClickListener {
            val selectedPreference = dietarySpinner.selectedItem.toString()
            lifecycleScope.launch {
                preferencesManager.saveDietaryPreference(selectedPreference)
                Toast.makeText(context, "Preferences saved!", Toast.LENGTH_SHORT).show()
            }
        }

        logoutButton.setOnClickListener {
            auth.signOut()
            lifecycleScope.launch {
                preferencesManager.clearPreferences()
            }
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        return view
    }

    private fun saveProfilePicture(uri: Uri): String? {
        return try {
            val context = requireContext()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            val fileName = "profile_picture.jpg"
            val directory = File(context.filesDir, "profile_images")

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
}