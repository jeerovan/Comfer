package com.jeerovan.comfer

import android.content.Context
import androidx.core.content.edit
import com.jeerovan.comfer.utils.CommonUtil
import kotlinx.serialization.json.Json
import java.util.Calendar

object PreferenceManager {
    private const val PREF_BACKGROUND_IMAGE = "background_image"
    private const val PREFS_NAME = "com.jeerovan.comfer.Prefs"
    private const val KEY_WALLPAPER_MOTION = "wallpaper_motion"

    private const val WALLPAPER_ON_LOCK_SCREEN = "wallpaper_on_lock_screen"
    private const val KEY_ICON_SIZE = "icon_size"
    private const val KEY_IMAGE_URL = "image_url"
    private const val DUMMY_NAME = "dummy_name"
    private const val USER_NAME = "user_name"
    private const val KEY_IMAGE_DATA = "image_data"
    private const  val KEY_TEMP_IMAGE_DATA = "temp_image_data"
    private const val IMAGE_AVAILABLE = "image_available"

    private const val ENHANCED_ICONS = "enhanced_icons"

    private const val FEEDBACK_DIALOG = "feedback_dialog"

    private const val WALLPAPER_SET = "wallpaper_set"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun onFirstOpen(context: Context){
        val milliseconds = System.currentTimeMillis()
        val millisecondsString = milliseconds.toString()
        val dummyName = CommonUtil.randomCode(millisecondsString,8)
        getPrefs(context).edit {
            putString(DUMMY_NAME, dummyName)
        }
    }

    fun setWallpaperMotion(context: Context, enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_WALLPAPER_MOTION, enabled)
        }
    }

    fun getWallpaperMotion(context: Context, default: Boolean = true): Boolean {
        return getPrefs(context).getBoolean(KEY_WALLPAPER_MOTION, default)
    }

    fun getWallpaperOnLockScreen(context: Context,default: Boolean = false): Boolean {
        return getPrefs(context).getBoolean(WALLPAPER_ON_LOCK_SCREEN, default)
    }
    fun setWallpaperOnLockScreen(context: Context,enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(WALLPAPER_ON_LOCK_SCREEN, enabled)
        }
    }

    fun setEnhancedIcons(context: Context, enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(ENHANCED_ICONS,enabled)
        }
    }

    fun getEnhancedIcons(context: Context,default: Boolean): Boolean {
        return getPrefs(context).getBoolean(ENHANCED_ICONS,default)
    }
    fun setIconSize(context: Context, size: Int) {
        getPrefs(context).edit {
            putInt(KEY_ICON_SIZE, size)
        }
    }

    fun getIconSize(context: Context, default: Int = 48): Int {
        return getPrefs(context).getInt(KEY_ICON_SIZE, default)
    }

    fun getBackgroundImagePath(context: Context): String? {
        return getPrefs(context).getString(PREF_BACKGROUND_IMAGE, null)
    }

    fun setBackgroundImagePath(context: Context, path: String) {
        getPrefs(context).edit {
            putString(PREF_BACKGROUND_IMAGE, path)
        }
    }

    fun getSwipeApp(context:Context,swipeDirection:String):String?{
        return getPrefs(context).getString("${swipeDirection}_swipe_app",null)
    }

    fun setSwipeApp(context:Context,swipeDirection:String,appPackage:String){
        getPrefs(context).edit {
            putString("${swipeDirection}_swipe_app",appPackage)
        }
    }

    fun getBoolean(context: Context,key:String):Boolean{
        return getPrefs(context).getBoolean(key,false)
    }
    fun setBoolean(context: Context,key:String,state:Boolean) {
        getPrefs(context).edit {
            putBoolean(key,state)
        }
    }

    fun getImageUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_IMAGE_URL, null)
    }

    fun setImageUrl(context: Context, url: String) {
        getPrefs(context).edit {
            putString(KEY_IMAGE_URL, url)
        }
    }

    fun setUsername(context: Context,name:String){
        getPrefs(context).edit {
            putString(USER_NAME, name)
        }
    }

    fun getUsername(context: Context):String? {
        val prefs = getPrefs(context)
        return prefs.getString(USER_NAME,prefs.getString(DUMMY_NAME,""))
    }

    fun saveImageData(context: Context, imageData: ImageData) {
        val previousImageData:ImageData? = getImageData(context);
        if (previousImageData != null){
            if(previousImageData.id == imageData.id){
                // must be saved/overwritten for changes other than image url
                val jsonString = Json.encodeToString(imageData)
                getPrefs(context).edit {
                    putString(KEY_IMAGE_DATA, jsonString)
                }
            } else {
                val jsonString = Json.encodeToString(imageData)
                getPrefs(context).edit {
                    putString(KEY_TEMP_IMAGE_DATA, jsonString)
                    putBoolean(IMAGE_AVAILABLE,true)
                }
            }
        } else {
            // must be saved/overwritten for changes other than image url
            val jsonString = Json.encodeToString(imageData)
            getPrefs(context).edit {
                putString(KEY_TEMP_IMAGE_DATA, jsonString)
                putBoolean(IMAGE_AVAILABLE,true)
            }
        }
    }

    fun getImageData(context: Context): ImageData? {
        val prefs = getPrefs(context)
        val jsonString = prefs
            .getString(KEY_IMAGE_DATA, null)
        return jsonString?.let { Json.decodeFromString(it) }
    }
    fun getTempImageData(context: Context): ImageData? {
        val prefs = getPrefs(context)
        val jsonString = prefs
            .getString(KEY_TEMP_IMAGE_DATA, null)
        return jsonString?.let { Json.decodeFromString(it) }
    }

    fun newImageAvailable(context: Context):Boolean{
        return getPrefs(context).getBoolean(IMAGE_AVAILABLE,false)
    }
    fun setImageDownloaded(context: Context){
        val tempImageData:ImageData? = getTempImageData(context)
        val jsonString = Json.encodeToString(tempImageData)
        getPrefs(context).edit {
            putBoolean(IMAGE_AVAILABLE,false)
            putString(KEY_IMAGE_DATA, jsonString)
        }
    }

    fun getHour(context: Context):Int{
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val prefs = getPrefs(context)
        val existingHour = prefs.getInt("now_hour",0)
//        return if(existingHour == 23){
//            1
//        } else {
//            existingHour + 1
//        }
        return if(existingHour != hour) {
            hour
        } else {
            0
        }
    }
    fun setHour(context: Context,hour:Int){
        getPrefs(context).edit {
            putInt("now_hour",hour)
        }
    }

    fun getFeedbackDialogShown(context: Context):Boolean {
        return getPrefs(context).getBoolean(FEEDBACK_DIALOG,false)
    }
    fun setFeedbackDialogShown(context: Context){
        getPrefs(context).edit {
            putBoolean(FEEDBACK_DIALOG,true)
        }
    }

    fun getWallpaperApplied(context: Context):Boolean {
        return getPrefs(context).getBoolean(WALLPAPER_SET,true)
    }
    fun setWallpaperApplied(context: Context,applied : Boolean){
        getPrefs(context).edit {
            putBoolean(WALLPAPER_SET,applied)
        }
    }
}
