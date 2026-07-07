package com.revio.app.data.repository

import com.revio.app.data.remote.api.UploadImageApi
import com.revio.app.data.remote.dto.user.UploadImageRequest
import com.revio.app.data.remote.dto.user.UploadUrlResponse
import com.revio.app.core.network.ApiResult
import com.revio.app.core.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface ImageRepository {
    suspend fun getUploadUrl(request: UploadImageRequest): ApiResult<UploadUrlResponse>
    suspend fun uploadImageAndGetPublicUrl(request: UploadImageRequest, imageBytes: ByteArray, mimeType: String): String
}

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val uploadImageApi: UploadImageApi
) : ImageRepository {

    override suspend fun getUploadUrl(request: UploadImageRequest): ApiResult<UploadUrlResponse> {
        return safeApiCall { uploadImageApi.getUploadUrl(request) }
    }

    override suspend fun uploadImageAndGetPublicUrl(request: UploadImageRequest, imageBytes: ByteArray, mimeType: String): String {
        val uploadResponse = getUploadUrl(request)
        return when (uploadResponse) {
            is ApiResult.Success -> {
                uploadToS3(uploadResponse.data.uploadUrl, imageBytes, mimeType)
                uploadResponse.data.publicUrl
            }
            is ApiResult.Error -> throw IOException("Failed to get upload URL: ${uploadResponse.message}")
        }
    }

    private suspend fun uploadToS3(
        uploadUrl: String,
        imageBytes: ByteArray,
        mimeType: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(uploadUrl)
                .put(imageBytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    throw IOException("Upload failed with status code ${it.code}")
                }
                true
            }
        }
    }
}
