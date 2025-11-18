package com.example.myapplication.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ImgurUploader {

    private const val UPLOAD_URL = "https://api.imgur.com/3/upload"
    private const val ANONYMOUS_CLIENT_ID = "546c25a59c58ad7"

    suspend fun uploadImage(imageFile: File): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap == null) {
                println("Failed to decode image file")
                return@withContext null
            }

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val imageBytes = outputStream.toByteArray()

            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val url = URL(UPLOAD_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Client-ID $ANONYMOUS_CLIENT_ID")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            connection.outputStream.use { outputStream ->
                val writer = outputStream.bufferedWriter()

                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"image\"\r\n")
                writer.write("Content-Type: image/jpeg\r\n\r\n")
                writer.flush()

                writer.write(base64Image)
                writer.write("\r\n")

                writer.write("--$boundary--\r\n")
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonResponse = JSONObject(response)
                val data = jsonResponse.getJSONObject("data")
                val imageUrl = data.getString("link")

                println("Imgur upload successful: $imageUrl")
                return@withContext imageUrl
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.readText()
                errorReader.close()
                println("Imgur upload failed with code: $responseCode")
                println("Error response: $errorResponse")
                return@withContext null
            }
        } catch (e: Exception) {
            println("Imgur upload exception: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }
}