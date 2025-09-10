package com.jeerovan.comfer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeerovan.comfer.utils.CommonUtil.downloadImage
import com.jeerovan.comfer.utils.CommonUtil.fetchImageData
import com.jeerovan.comfer.utils.CommonUtil.setWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        viewModelScope.launch {
            val applicationContext:Application = getApplication()
            val logger = LoggerManager(applicationContext)
            logger.setLog("MainViewModel","Loading")
            val imageData = PreferenceManager.getImageData(applicationContext)
            val backgroundImage = PreferenceManager.getBackgroundImagePath(applicationContext)
            if(imageData == null || backgroundImage == null){
                if(!isDownloading()) {
                    setDownloading(true)
                    // without dispatchers it will run on main ui thread
                    withContext(Dispatchers.IO){
                        fetchImageData(applicationContext)
                    }
                    delay(500)
                    withContext(Dispatchers.IO){
                        downloadImage(applicationContext)
                    }
                    delay(500)
                    // update uiState
                    val imageData = PreferenceManager.getImageData(applicationContext)
                    val filePath = PreferenceManager.getBackgroundImagePath(applicationContext)
                    // this is a first time fetch, do not set wallpaper on home screen as the app is not set default home app now
                    _uiState.update {
                        it.copy(
                            imageData = imageData,
                            imagePath = filePath
                        )
                    }
                    PreferenceManager.setWallpaperApplied(applicationContext,true)
                    setDownloading(false)
                }
            } else {
                if(_uiState.value.imageData != imageData || _uiState.value.imagePath != backgroundImage) {
                    PreferenceManager.setWallpaperApplied(applicationContext,true)
                    withContext(Dispatchers.IO){
                        setWallpaper(applicationContext)
                    }
                    _uiState.update {
                        it.copy(
                            imageData = imageData,
                            imagePath = backgroundImage
                        )
                    }
                }
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

}