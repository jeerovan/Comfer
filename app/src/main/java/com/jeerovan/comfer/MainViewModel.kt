package com.jeerovan.comfer

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeerovan.comfer.utils.CommonUtil.downloadImage
import com.jeerovan.comfer.utils.CommonUtil.fetchImageData
import com.jeerovan.comfer.utils.CommonUtil.setWallpaper
import com.jeerovan.comfer.utils.CommonUtil.setWallpaperThemedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState (
    val imageData: ImageData? = null,
    val imagePath:String? = null,
    val iconVersion:Int = 0,
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val logger = LoggerManager(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()
    private var isWorking = false
    private val _backPressEvent = MutableSharedFlow<Unit>()
    val backPressEvent = _backPressEvent.asSharedFlow()

    fun onBackButtonPressed() {
        viewModelScope.launch {
            _backPressEvent.emit(Unit)
        }
    }
    init {
       loadImageData()
    }
    fun loadImageData(){
        if(isWorking)return
        isWorking = true
        logger.setLog("MainViewModel","LoadImageData")
        viewModelScope.launch {
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
                        val bitmap = BitmapFactory.decodeFile(filePath)
                        setWallpaperThemedColors(applicationContext,bitmap)
                    }
                } else {
                    if (_uiState.value.imageData != imageData || _uiState.value.imagePath != backgroundImagePath) {
                        PreferenceManager.setWallpaperApplied(applicationContext, true)
                        _uiState.update {
                            it.copy(
                                imageData = imageData,
                                imagePath = backgroundImagePath
                            )
                        }
                        withContext(Dispatchers.IO){
                            setWallpaper(applicationContext)
                            val bitmap = BitmapFactory.decodeFile(backgroundImagePath)
                            setWallpaperThemedColors(applicationContext,bitmap)
                        }
                        _uiState.update {
                            it.copy(
                                iconVersion = _uiState.value.iconVersion + 1
                            )
                        }
                    }
                }
            }
            catch (e: Exception){
                logger.setLog("MainViewModel",e.toString())
            }
            finally {
                isWorking = false
            }
        }
    }
    fun checkLoadWallpaper(){
        val applyNow = PreferenceManager.getApplyWallpaperNow(getApplication())
        val monochrome = PreferenceManager.getMonochrome(getApplication())
        if(applyNow || monochrome){
            loadImageData()
            PreferenceManager.setApplyWallpaperNow(getApplication(),false)
        }
    }
}