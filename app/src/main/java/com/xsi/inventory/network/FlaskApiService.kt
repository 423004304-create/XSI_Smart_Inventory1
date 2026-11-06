package com.xsi.inventory.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class Detection(
    @SerializedName("class")
    val className: String,
    val confidence: Double,
)

data class PredictionResponse(
    val success: Boolean,
    val detections: List<Detection>?
)

interface FlaskApiService {
    @Multipart
    @POST("predict")
    suspend fun predictImage(
        @Part image: MultipartBody.Part
    ): Response<PredictionResponse>
}
