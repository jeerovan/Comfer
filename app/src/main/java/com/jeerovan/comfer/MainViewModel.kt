package com.jeerovan.comfer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MainUiState (
    val imagePath:String? = null,
    val iconVersion:Int = 0,
    val isDefaultLauncher: Boolean = false
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
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
            StartupCoordinator.awaitReady()
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
            StartupCoordinator.awaitReady()
            application.dataStore.data
                .map { it[PreferenceKeys.WALLPAPER_CHANGE] ?: 0L }
                .distinctUntilChanged() // Critical: ignore unrelated DataStore updates
                .collect { timestamp ->
                    if(timestamp > 0L) {
                        Log.d("MainViewModel", "Wallpaper Changed At: $timestamp")
                        changeWallpaper()
                        // Consume the event so it doesn't replay on app restart
                        application.dataStore.edit { preferences ->
                            preferences[PreferenceKeys.WALLPAPER_CHANGE] = 0L
                        }
                    }
                }
        }
        // Observer for Wallpaper Changes
        viewModelScope.launch {
            StartupCoordinator.awaitReady()
            application.dataStore.data
                .map { it[PreferenceKeys.WALLPAPER_RESET] ?: 0L }
                .distinctUntilChanged() // Critical: ignore unrelated DataStore updates
                .collect { timestamp ->
                    if(timestamp > 0L) {
                        Log.d("MainViewModel", "Wallpaper Reset At: $timestamp")
                        reapplyWallpaper()
                        // Consume the event so it doesn't replay on app restart
                        application.dataStore.edit { preferences ->
                            preferences[PreferenceKeys.WALLPAPER_RESET] = 0L
                        }
                    }
                }
        }
    }
    fun reloadImagePath() {
        viewModelScope.launch {
            StartupCoordinator.awaitReady()
            val context: Context = getApplication()
            val backgroundImage = withContext(Dispatchers.IO) {
                PreferenceManager.getBackgroundImagePath(context)
            }

            if (_uiState.value.imagePath == null) {
                _uiState.update { it.copy(imagePath = backgroundImage) }
            }

            withContext(Dispatchers.IO) {
                PreferenceManager.setWallpaperApplied(context, true)
            }

            val defaultLauncher = withContext(Dispatchers.IO) {
                isDefaultLauncher(context)
            }
            _uiState.update { it.copy(isDefaultLauncher = defaultLauncher) }

            if (defaultLauncher) {
                val appliedWallpaperImage = withContext(Dispatchers.IO) {
                    PreferenceManager.getAppliedWallpaperImage(context)
                }
                if (appliedWallpaperImage != backgroundImage) {
                    reapplyWallpaper()
                }
            }
        }
    }
    fun clearImagePath() { // unloads on screen off
        viewModelScope.launch {
            _uiState.update { it.copy(imagePath = null) }
        }
    }
    fun loadBackgroundData(){
        if(backgroundLoadJob?.isActive == true) return
        backgroundLoadJob = viewModelScope.launch {
            try {
                StartupCoordinator.awaitReady()
                val applicationContext: Application = getApplication()
                
                // Move preferences access to IO
                val (imageData, backgroundImagePath) = withContext(Dispatchers.IO) {
                    PreferenceManager.getImageData(applicationContext) to 
                    PreferenceManager.getBackgroundImagePath(applicationContext)
                }

                if (imageData == null || backgroundImagePath == null) {
                    WallpaperWorkCoordinator.runExclusive {
                        fetchImageData(applicationContext)
                        downloadImage(applicationContext)
                        
                        // update uiState
                        val filePath = PreferenceManager.getBackgroundImagePath(applicationContext)
                        if (filePath != null) {
                            val imageFile = File(filePath)
                            setWallpaperThemedColors(applicationContext, imageFile)
                            // check if current image was already set as wallpaper
                            if (PreferenceManager.getAppliedWallpaperImage(applicationContext) != filePath){
                                setWallpaper(applicationContext)
                            }
                            PreferenceManager.setWallpaperApplied(applicationContext, true)
                            
                            _uiState.update { it.copy(imagePath = filePath) }
                        }
                    }
                } else {
                    if (_uiState.value.imagePath != backgroundImagePath) {
                        _uiState.update {
                            it.copy(
                                imagePath = backgroundImagePath,
                                iconVersion = _uiState.value.iconVersion + 1
                            )
                        }
                    }
                }
            }
            catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
            catch (e: Exception){
                Log.e("MainViewModel",e.toString())
            }
        }
    }
    fun reapplyWallpaper(){
        viewModelScope.launch {
            StartupCoordinator.awaitReady()
            WallpaperWorkCoordinator.runExclusive {
                val context: Context = getApplication()
                // A lifecycle resume may enqueue this while a download/apply still owns
                // the coordinator. Re-check after acquiring it so the completed apply's
                // marker can invalidate this now-stale request.
                val desiredImage = PreferenceManager.getBackgroundImagePath(context)
                val appliedImage = PreferenceManager.getAppliedWallpaperImage(context)
                if (desiredImage != null && desiredImage != appliedImage) {
                    setWallpaper(context)
                }
            }
        }
    }
    fun changeWallpaper(){
        if(wallpaperChangeJob?.isActive == true) return
        wallpaperChangeJob = viewModelScope.launch {
            val context:Context = getApplication()
            try {
                StartupCoordinator.awaitReady()
                WallpaperWorkCoordinator.runExclusive {
                    fetchImageData(context, manualChange = true)
                    downloadImage(context)
                }
            }
            catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
            catch (e: Exception){
                Log.e("MainViewModel",e.toString())
            }
        }
    }
}
