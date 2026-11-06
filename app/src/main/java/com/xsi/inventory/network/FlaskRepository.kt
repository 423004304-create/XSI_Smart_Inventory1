package com.xsi.inventory.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

object FlaskRepository {
    private const val TAG = "FlaskRepository"

    suspend fun uploadAndPredict(imageFile: File): List<Detection>? {
        return try {
            val compressedFile = compressImage(imageFile)
            val requestFile = compressedFile.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", compressedFile.name, requestFile)

            val response = RetrofitClient.apiService.predictImage(body)
            if ((compressedFile.exists()) && (compressedFile != imageFile)) {
                compressedFile.delete()
            }

            if (response.isSuccessful) {
                val prediction = response.body()
                if ((prediction != null) && (prediction.success)) {
                    prediction.detections
                } else {
                    Log.e(TAG, "Prediction failed: ${response.errorBody()?.string()}")
                    null
                }
            } else {
                Log.e(TAG, "Server error: ${response.code()} - ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during prediction request", e)
            null
        }
    }

    private fun compressImage(file: File): File {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val maxHeight = 1024
            val maxWidth = 1024
            var inSampleSize = 1
            if ((options.outHeight > maxHeight) || (options.outWidth > maxWidth)) {
                val halfHeight: Int = options.outHeight / 2
                val halfWidth: Int = options.outWidth / 2
                while (((halfHeight / inSampleSize) >= maxHeight) && ((halfWidth / inSampleSize) >= maxWidth)) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return file

            val compressedFile = File(file.parent, "compressed_${System.currentTimeMillis()}.jpg")
            FileOutputStream(compressedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()
            compressedFile
        } catch (_: Exception) {
            file
        }
    }
}
