package com.jeerovan.comfer

import android.content.Context
import androidx.compose.ui.graphics.Shape
import androidx.core.content.edit
import com.jeerovan.comfer.utils.CommonUtil
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromString
import kotlinx.serialization.json.Json
import java.util.Calendar

object PreferenceManager {
    private const val PREF_BACKGROUND_IMAGE = "background_image"
    private const val PREFS_NAME = "com.jeerovan.comfer.Prefs"
    private const val KEY_WALLPAPER_MOTION = "wallpaper_motion"

    private const val WALLPAPER_ON_LOCK_SCREEN = "wallpaper_on_lock_screen"
    private const val KEY_ICON_SIZE = "icon_size"
    private const val KEY_ICON_SHAPE = "icon_shape"
    private const val KEY_IMAGE_URL = "image_url"
    private const val DUMMY_NAME = "dummy_name"
    private const val USER_NAME = "user_name"
    private const val KEY_IMAGE_DATA = "image_data"
    private const  val KEY_TEMP_IMAGE_DATA = "temp_image_data"
    private const val IMAGE_AVAILABLE = "image_available"
    private const val FEEDBACK_DIALOG = "feedback_dialog"
    private const val WALLPAPER_SET = "wallpaper_set"
    private const val QUICK_APPS_LAYOUT = "quick_apps_layout"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBoolean(context: Context,key:String,default: Boolean):Boolean{
        /*val cachedValue = AppCache.get(key) as? Boolean
        if (cachedValue != null) {
            return cachedValue
        }*/
        val prefValue = getPrefs(context).getBoolean(key,default)
        //AppCache.set(key, prefValue)
        return prefValue
    }
    fun setBoolean(context: Context,key:String,state:Boolean) {
        //AppCache.set(key, state)
        getPrefs(context).edit {
            putBoolean(key,state)
        }
    }
    fun setString(context: Context,key:String,string: String?) {
        //AppCache.set(key, string)
        getPrefs(context).edit {
            putString(key,string)
        }
    }
    fun getString(context: Context,key:String,default: String?):String?{
        /*val cachedValue = AppCache.get(key) as? String
        if (cachedValue != null) {
            return cachedValue
        }*/
        val prefValue = getPrefs(context).getString(key,default) ?: default
        /*if(prefValue != null){
            AppCache.set(key, prefValue)
        }*/
        return prefValue
    }

    fun setInt(context: Context,key:String,int: Int) {
        //AppCache.set(key, int)
        getPrefs(context).edit {
            putInt(key,int)
        }
    }
    fun getInt(context: Context,key:String,default: Int ):Int{
        /*val cachedValue = AppCache.get(key) as? Int
        if (cachedValue != null) {
            return cachedValue
        }*/
        val prefValue = getPrefs(context).getInt(key,default)
        //AppCache.set(key, prefValue)
        return prefValue
    }

    fun onFirstOpen(context: Context){
        val milliseconds = System.currentTimeMillis()
        val millisecondsString = milliseconds.toString()
        val dummyName = CommonUtil.randomCode(millisecondsString,8)
        setDummyName(context,dummyName)
        setQuickAppsLayout(context,"circular")
    }

    fun setQuickAppsLayout(context: Context,layout:String){
        setString(context,QUICK_APPS_LAYOUT,layout)
    }
    fun getQuickAppsLayout(context: Context):String? {
        return getString(context,QUICK_APPS_LAYOUT,"linear")
    }

    // Example: setWallpaperMotion
    fun setWallpaperMotion(context: Context, enabled: Boolean) {
        setBoolean(context,KEY_WALLPAPER_MOTION,enabled)
    }

    // Example: getWallpaperMotion
    fun getWallpaperMotion(context: Context, default: Boolean = true): Boolean {
        return getBoolean(context,KEY_WALLPAPER_MOTION,default)
    }

    fun getWallpaperOnLockScreen(context: Context,default: Boolean = false): Boolean {
        return getBoolean(context,WALLPAPER_ON_LOCK_SCREEN,default)
    }
    fun setWallpaperOnLockScreen(context: Context,enabled: Boolean) {
        setBoolean(context,WALLPAPER_ON_LOCK_SCREEN,enabled)
    }
    fun setIconSize(context: Context, size: Int) {
        setInt(context,KEY_ICON_SIZE,size)
    }

    // Example: getIconSize
    fun getIconSize(context: Context, default: Int = 48): Int {
        return getInt(context,KEY_ICON_SIZE,default)
    }

    fun setIconShape(context: Context, shape: String) {
        setString(context,KEY_ICON_SHAPE,shape)
    }

    fun getIconShapeString(context: Context, default: String = "circle"): String {
        return getString(context,KEY_ICON_SHAPE,default) ?: default
    }

    fun getIconShape(context: Context, default: String = "circle"): Shape {
        val iconShapeString = getIconShapeString(context, default)
        return getShapeFromString(iconShapeString)
    }

    fun getBackgroundImagePath(context: Context): String? {
        return getString(context,PREF_BACKGROUND_IMAGE,null)
    }

    fun setBackgroundImagePath(context: Context, path: String) {
        setString(context,PREF_BACKGROUND_IMAGE,path)
    }

    fun getSwipeApp(context:Context,swipeDirection:String):String?{
        return getString(context,"${swipeDirection}_swipe_app",null)
    }

    fun setSwipeApp(context:Context,swipeDirection:String,appPackage:String?){
        setString(context,"${swipeDirection}_swipe_app", appPackage)
    }

    fun getWidgetsOnSwipe(context:Context, swipeDirection:String):Boolean{
        return getBoolean(context,"${swipeDirection}_swipe_widgets",false)
    }

    fun setWidgetsOnSwipe(context:Context, swipeDirection:String,set: Boolean){
        setBoolean(context,"${swipeDirection}_swipe_widgets", set)
    }

    fun setUsername(context: Context,name:String){
        setString(context,USER_NAME,name)
    }

    fun getUsername(context: Context):String? {
        return getString(context,USER_NAME,getDummyName(context))
    }

    fun setDummyName(context: Context,name:String){
        setString(context,DUMMY_NAME,name)
    }

    fun getDummyName(context: Context):String? {
        return getString(context,DUMMY_NAME,"")
    }

    fun saveImageData(context: Context, imageData: ImageData) {
        val previousImageData:ImageData? = getImageData(context);
        if (previousImageData != null){
            if(previousImageData.id == imageData.id){
                // must be saved/overwritten for changes other than image url
                val jsonString = Json.encodeToString(imageData)
                setImageDataString(context,jsonString)
            } else {
                val jsonString = Json.encodeToString(imageData)
                setTempImageData(context,jsonString)
            }
        } else {
            // must be saved/overwritten for changes other than image url
            val jsonString = Json.encodeToString(imageData)
            setTempImageData(context,jsonString)
        }
    }


    fun setImageDataString(context: Context,jsonString:String) {
        setString(context,KEY_IMAGE_DATA,jsonString)
    }
    fun getImageDataString(context: Context,default:String?): String? {
        return getString(context,KEY_IMAGE_DATA,default)
    }
    fun updateImageData(context: Context, white: Boolean){
        val imageData:ImageData? = getImageData(context)
        imageData?.position = "TopCenter"
        imageData?.color = if (white) "White" else "Black"
        val jsonString = Json.encodeToString(imageData)
        setImageDataString(context,jsonString)
    }
    fun getImageData(context: Context): ImageData? {
        val jsonString = getImageDataString(context,null)
        return jsonString?.let { Json.decodeFromString(it) }
    }
    fun getTempImageData(context: Context): ImageData? {
        val jsonString = getString(context,KEY_TEMP_IMAGE_DATA,null)
        return jsonString?.let { Json.decodeFromString(it) }
    }
    fun setTempImageData(context: Context,jsonString:String){
        setString(context,KEY_TEMP_IMAGE_DATA,jsonString)
        setBoolean(context,IMAGE_AVAILABLE,true)
    }

    fun newImageAvailable(context: Context):Boolean{
        return getBoolean(context,IMAGE_AVAILABLE,false)
    }
    fun setImageDownloaded(context: Context){
        val tempImageData:ImageData? = getTempImageData(context)
        val jsonString = Json.encodeToString(tempImageData)
        setImageDataString(context,jsonString)
        setBoolean(context,IMAGE_AVAILABLE,false)
    }

    fun getHour(context: Context):Int{
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val existingHour = getInt(context,"now_hour",0)
        return if(existingHour != hour) {
            hour
        } else {
            0
        }
    }
    fun setHour(context: Context,hour:Int){
        setInt(context,"now_hour",hour)
    }

    fun getFeedbackDialogShown(context: Context):Boolean {
        return getBoolean(context,FEEDBACK_DIALOG,false)
    }
    fun setFeedbackDialogShown(context: Context){
        setBoolean(context,FEEDBACK_DIALOG,true)
    }

    fun getWallpaperApplied(context: Context):Boolean {
        return getBoolean(context,WALLPAPER_SET,true)
    }
    fun setWallpaperApplied(context: Context,applied : Boolean){
        setBoolean(context,WALLPAPER_SET,applied)
    }
}
