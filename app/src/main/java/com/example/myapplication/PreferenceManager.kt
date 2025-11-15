package com.example.myapplication

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        val DIETARY_PREFERENCE_KEY = stringPreferencesKey("dietary_preference")
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val PROFILE_PICTURE_KEY = stringPreferencesKey("profile_picture")
    }

    val dietaryPreference: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DIETARY_PREFERENCE_KEY] ?: "None"
    }

    val userEmail: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_EMAIL_KEY] ?: ""
    }

    val profilePicture: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PROFILE_PICTURE_KEY] ?: ""
    }

    suspend fun saveDietaryPreference(preference: String) {
        context.dataStore.edit { preferences ->
            preferences[DIETARY_PREFERENCE_KEY] = preference
        }
    }

    suspend fun saveUserEmail(email: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_EMAIL_KEY] = email
        }
    }

    suspend fun saveProfilePicture(path: String) {
        context.dataStore.edit { preferences ->
            preferences[PROFILE_PICTURE_KEY] = path
        }
    }

    suspend fun clearPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}