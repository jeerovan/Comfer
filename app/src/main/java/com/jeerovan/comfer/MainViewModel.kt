package com.jeerovan.comfer

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeerovan.comfer.utils.CommonUtil.downloadImage
import com.jeerovan.comfer.utils.CommonUtil.fetchImageData
import com.jeerovan.comfer.utils.CommonUtil.isDefaultLauncher
import com.jeerovan.comfer.utils.CommonUtil.setWallpaper
import com.jeerovan.comfer.utils.CommonUtil.setWallpaperThemedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MainUiState (
    val imageData: ImageData? = null,
    val imagePath:String? = null,
    val iconVersion:Int = 0,
    val isDefaultLauncher: Boolean = false
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val logger = LoggerManager(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()
    private var backgroundLoadJob: Job? = null
    private var wallpaperChangeJob: Job? = null
    private val _backPressEvent = MutableSharedFlow<Unit>()
    val backPressEvent = _backPressEvent.asSharedFlow()
    fun onBackButtonPressed() {
        viewModelScope.launch {
            _backPressEvent.emit(Unit)
        }
    }

    init {
        // Observer for Wallpaper Updates
        viewModelScope.launch {
            application.dataStore.data
                .map { it[PreferenceKeys.WALLPAPER_UPDATE] ?: 0L }
                .distinctUntilChanged() // Critical: ignore unrelated DataStore updates
                .collect { timestamp ->
                    Log.d("MainViewModel", "Wallpaper Updated At: $timestamp")
                    loadBackgroundData()
                }
        }

        // Observer for Wallpaper Changes
        viewModelScope.launch {
            application.dataStore.data
                .map { it[PreferenceKeys.WALLPAPER_CHANGE] ?: 0L }
                .distinctUntilChanged() // Critical: ignore unrelated DataStore updates
                .collect { timestamp ->
                    if(timestamp > 0L) {
                        Log.d("MainViewModel", "Wallpaper Changed At: $timestamp")
                        changeWallpaper()
                    }
                }
        }
    }
    fun reloadImageData(){
        viewModelScope.launch {
            val imageData = PreferenceManager.getImageData(getApplication())
            _uiState.update {
                it.copy(imageData = imageData)
            }
        }
    }
    fun loadBackgroundData(){
        if(backgroundLoadJob?.isActive == true) return
        backgroundLoadJob = viewModelScope.launch {
            try {
                val applicationContext: Application = getApplication()
                val imageData = PreferenceManager.getImageData(applicationContext)
                val backgroundImagePath = PreferenceManager.getBackgroundImagePath(applicationContext)
                if (imageData == null || backgroundImagePath == null) {
                    withContext(Dispatchers.IO) {
                        fetchImageData(applicationContext)
                    }
                    delay(500)
                    withContext(Dispatchers.IO) {
                        downloadImage(applicationContext)
                    }
                    delay(500)
                    // update uiState
                    val imageData = PreferenceManager.getImageData(applicationContext)
                    val filePath = PreferenceManager.getBackgroundImagePath(applicationContext)
                    // this is a first time fetch, do not set wallpaper on home screen as the app is not set default home app now
                    if (imageData != null && filePath != null) {
                        logger.setLog("MainViewModel","Updating State with image path & data")
                        _uiState.update {
                            it.copy(
                                imageData = imageData,
                                imagePath = filePath
                            )
                        }
                    }
                    PreferenceManager.setWallpaperApplied(applicationContext, true)
                    withContext(Dispatchers.IO){
                        setWallpaperThemedColors(applicationContext, File(filePath))
                    }
                } else {
                    if (_uiState.value.imageData != imageData || _uiState.value.imagePath != backgroundImagePath) {
                        _uiState.update {
                            it.copy(
                                imageData = imageData,
                                imagePath = backgroundImagePath,
                                iconVersion = _uiState.value.iconVersion + 1
                            )
                        }
                    }
                }
                val isDefaultLauncher = isDefaultLauncher(applicationContext)
                _uiState.update {
                    it.copy(isDefaultLauncher = isDefaultLauncher)
                }
            }
            catch (e: Exception){
                logger.setLog("MainViewModel",e.toString())
            }
        }
    }
    fun changeWallpaper(){
        if(wallpaperChangeJob?.isActive == true) return
        wallpaperChangeJob = viewModelScope.launch {
            val context:Context = getApplication()
            try {
                delay(100)
                withContext(Dispatchers.IO) {
                    fetchImageData(context, manualChange = true)
                }
                delay(100)
                withContext(Dispatchers.IO) {
                    downloadImage(context)
                }
            }
            catch (e: Exception){
                logger.setLog("MainViewModel",e.toString())
            }
        }
    }
}