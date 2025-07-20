package com.jeerovan.comfer

import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ListenableWorker
import coil.Coil
import coil.request.ImageRequest
import coil.request.ImageResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

data class MainUiState (
    val imageData: ImageData? = null,
    val imagePath:String? = null,
    val downloading:Boolean = false
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadImageData()
    }
    fun loadImageData(){
        Log.i("MainViewModel","Loading")
        viewModelScope.launch {
            val applicationContext:Application = getApplication()
            val imageData = PreferenceManager.getImageData(applicationContext)
            val backgroundImage = PreferenceManager.getBackgroundImagePath(applicationContext)
            _uiState.update {
                it.copy(
                    imageData = imageData,
                    imagePath = backgroundImage
                )
            }
        }
    }

    private fun setDownloading(downloading: Boolean){
        _uiState.update {
            it.copy(
                downloading = downloading
            )
        }
    }
    private fun isDownloading():Boolean{
        return _uiState.value.downloading
    }

    suspend fun fetchImageData(applicationContext: Context){
        try {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val name = PreferenceManager.getUsername(applicationContext)
            val client = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                    })
                }
            }
            val response: ImageData = client.get("https://comfer.jeerovan.com/api") {
                parameter("name",name)
                parameter("hour", hour)
            }.body()
            Log.d("ImageWorker",response.toString())
            PreferenceManager.saveImageData(applicationContext, response)
            client.close()
            Log.d("ImageWorker", "Successfully fetched and saved image URL: ${response.imageUrl}")

        } catch (e: Exception) {
            Log.e("ImageWorker", "Error fetching image", e)

        }
    }
    fun fetchImage(){
        Log.i("MainViewModel","Fetch Image")
        viewModelScope.launch {
            if(!isDownloading()) {
                setDownloading(true)
                val applicationContext: Application = getApplication()
                // first fetch imageData
                fetchImageData(applicationContext)

                delay(500)
                if (PreferenceManager.newImageAvailable(applicationContext)) {
                    Log.i("MainViewModel", "Downloading New Image")
                    val tempImageData: ImageData? =
                        PreferenceManager.getTempImageData(applicationContext)
                    if (tempImageData != null) {
                        val imageUrl = tempImageData.imageUrl
                        val request = ImageRequest.Builder(applicationContext)
                            .data(imageUrl)
                            .build()
                        val result = Coil.imageLoader(applicationContext).execute(request)
                        if (result is ImageResult) {
                            val drawable = result.drawable
                            if (drawable != null) {
                                val file = File(applicationContext.filesDir, "background.jpg")
                                val stream = FileOutputStream(file)
                                drawable.toBitmap()
                                    .compress(
                                        android.graphics.Bitmap.CompressFormat.JPEG,
                                        100,
                                        stream
                                    )
                                stream.close()
                                PreferenceManager.setBackgroundImagePath(
                                    applicationContext,
                                    file.absolutePath
                                )
                                PreferenceManager.setImageDownloaded(applicationContext)
                                // Set system wallpaper
                                val wallpaperManager =
                                    WallpaperManager.getInstance(applicationContext)
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    wallpaperManager.setBitmap(
                                        bitmap,
                                        null,
                                        true,
                                        WallpaperManager.FLAG_SYSTEM
                                    )
                                } else {
                                    wallpaperManager.setBitmap(bitmap)
                                }
                                // update uistate
                                val imageData = PreferenceManager.getImageData(applicationContext)
                                _uiState.update {
                                    it.copy(
                                        imageData = imageData,
                                        imagePath = file.absolutePath
                                    )
                                }
                            }
                        }
                    }
                }
                setDownloading(false)
            }
        }
    }
}