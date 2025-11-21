package com.example.myapplication

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class ProfileUiState(
    val email: String = "",
    val dietaryPreference: String = "None",
    val profilePicturePath: String = "",
    val isLoading: Boolean = false,
    val message: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserData()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            preferencesManager.userEmail.collect { email ->
                _uiState.value = _uiState.value.copy(email = email)
            }
        }

        viewModelScope.launch {
            preferencesManager.dietaryPreference.collect { preference ->
                _uiState.value = _uiState.value.copy(
                    dietaryPreference = preference,
                    isLoading = false
                )
            }
        }

        viewModelScope.launch {
            preferencesManager.profilePicture.collect { path ->
                _uiState.value = _uiState.value.copy(profilePicturePath = path)
            }
        }
    }

    fun updateDietaryPreference(preference: String) {
        viewModelScope.launch {
            preferencesManager.saveDietaryPreference(preference)
            _uiState.value = _uiState.value.copy(
                dietaryPreference = preference,
                message = "Preferences saved!"
            )
        }
    }

    fun updateProfilePicture(uri: Uri, context: Context) {
        viewModelScope.launch {
            val savedPath = saveProfilePicture(uri, context)
            savedPath?.let {
                preferencesManager.saveProfilePicture(it)
                _uiState.value = _uiState.value.copy(profilePicturePath = it)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun logout() {
        viewModelScope.launch {
            preferencesManager.clearPreferences()
        }
    }

    private fun saveProfilePicture(uri: Uri, context: Context): String? {
        return try {
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