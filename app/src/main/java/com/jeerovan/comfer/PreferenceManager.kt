package com.jeerovan.comfer

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.jeerovan.comfer.utils.CommonUtil
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromString
import kotlinx.serialization.json.Json
import java.util.Calendar
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.jeerovan.comfer.utils.KeyboardLocale
import java.io.File
import java.util.Locale

object PreferenceManager {
    private const val PREF_BACKGROUND_IMAGE = "background_image"
    private const val PREFS_NAME = "com.jeerovan.comfer.Prefs"
    private const val KEY_WALLPAPER_MOTION = "wallpaper_motion"
    private const val WALLPAPER_ON_LOCK_SCREEN = "wallpaper_on_lock_screen"
    private const val KEY_ICON_SIZE = "icon_size"
    private const val KEY_ICON_SHAPE = "icon_shape"
    private const val ICON_PACK_PACKAGE = "icon_pack_package"
    private const val DUMMY_NAME = "dummy_name"
    private const val USER_NAME = "user_name"
    private const val KEY_IMAGE_DATA = "image_data"
    private const  val KEY_TEMP_IMAGE_DATA = "temp_image_data"
    private const val IMAGE_AVAILABLE = "image_available"
    private const val FEEDBACK_DIALOG = "feedback_dialog"
    private const val WALLPAPER_SET = "wallpaper_set"
    private const val QUICK_APPS_LAYOUT = "quick_apps_layout"
    private const val APP_DRAWER_LAYOUT = "app_drawer_layout"
    private const val CUSTOM_WIDGETS = "custom_widgets"
    private const val WALLPAPER_DIRECTORY = "wallpaper_directory"
    private const val WALLPAPER_FREQUENCY = "wallpaper_frequency"
    private const val WALLPAPER_URI = "wallpaper_uri"
    private const val APPLIED_WALLPAPER_IMAGE = "applied_wallpaper_image"
    private const val ALPHABETICAL_ORDER = "alphabetical_order"
    private const val THEMED_ICONS = "themed_icons"
    private const val WALLPAPER_LIGHT_BG = "wallpaper_light_bg"
    private const val WALLPAPER_LIGHT_FG = "wallpaper_light_fg"
    private const val APP_LIST_UPDATE_COUNTER = "apps_list_update_counter"
    private const val WALLPAPER_DARK_BG = "wallpaper_dark_bg"
    private const val WALLPAPER_DARK_FG = "wallpaper_dark_fg"
    private const val WALLPAPER_TEXT_FG = "wallpaper_text_fg"
    private const val WALLPAPER_TEXT_BG = "wallpaper_text_bg"
    private const val AUTO_WALLPAPER = "auto_wallpaper"
    private const val MONOCHROME = "monochrome"
    private const val APP_UPDATE_PROMPT_TIME = "app_update_prompt_time"
    private const val APP_UPDATE_PROMPT_COUNTER = "app_update_prompt_counter"
    private const val KEYBOARD_LOCALE = "keyboard_locale"
    private const val BATTERY_SAVER_MODE = "battery_saver_mode"
    private const val TOP_BAR_VISIBLE = "top_bar_visible"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun clear(context: Context,key: String){
        getPrefs(context).edit { remove(key) }
    }
    fun hasKey(context: Context,key:String): Boolean{
        return getPrefs(context).contains(key)
    }
    fun getBoolean(context: Context,key:String,default: Boolean):Boolean{
        val prefValue = getPrefs(context).getBoolean(key,default)
        return prefValue
    }
    fun setBoolean(context: Context,key:String,state:Boolean) {
        getPrefs(context).edit {
            putBoolean(key,state)
        }
    }
    fun getFloat(context: Context,key:String,default: Float):Float{
        val prefValue = getPrefs(context).getFloat(key,default)
        return prefValue
    }
    fun setFloat(context: Context,key:String,value:Float) {
        getPrefs(context).edit {
            putFloat(key,value)
        }
    }
    fun getLong(context: Context,key:String,default: Long): Long {
        val prefValue = getPrefs(context).getLong(key,default)
        return prefValue
    }
    fun setLong(context: Context,key:String,value:Long) {
        getPrefs(context).edit {
            putLong(key,value)
        }
    }
    fun setString(context: Context,key:String,string: String?) {
        getPrefs(context).edit {
            putString(key,string)
        }
    }
    fun getString(context: Context,key:String,default: String?):String?{
        val prefValue = getPrefs(context).getString(key,default) ?: default
        return prefValue
    }

    fun setInt(context: Context,key:String,int: Int) {
        getPrefs(context).edit {
            putInt(key,int)
        }
    }
    fun getInt(context: Context,key:String,default: Int ):Int{
        val prefValue = getPrefs(context).getInt(key,default)
        return prefValue
    }

    fun onFirstOpen(context: Context){
        val milliseconds = System.currentTimeMillis()
        val millisecondsString = milliseconds.toString()
        val dummyName = CommonUtil.randomCode(millisecondsString,10)
        setDummyName(context,dummyName)
        setQuickAppsLayout(context,"circular")
    }
    fun isLightHour(context: Context): Boolean {
        if (isBatterySaver(context)) {
            return false
        } else {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            return hour > 6 && hour < 19
        }
    }
    fun setThemedColors(context: Context,
                        lightBg:Int,
                        lightFg:Int,
                        darkBg:Int,
                        darkFg:Int,
                        textFg:Int,
                        textBg:Int){
        setInt(context,WALLPAPER_LIGHT_FG,lightFg)
        setInt(context,WALLPAPER_LIGHT_BG,lightBg)
        setInt(context,WALLPAPER_DARK_FG,darkFg)
        setInt(context,WALLPAPER_DARK_BG,darkBg)
        setInt(context,WALLPAPER_TEXT_FG,textFg)
        setInt(context,WALLPAPER_TEXT_BG,textBg)
    }
    fun getThemedColors(context: Context): WallpaperThemeColors {
        if(getMonochrome(context)){
            return getMonoThemedColors(context)
        }
        return WallpaperThemeColors(
            getInt(context,WALLPAPER_LIGHT_BG,Color.White.copy(alpha = 0.7f).toArgb()),
            getInt(context,WALLPAPER_LIGHT_FG,Color.Black.toArgb()),
            getInt(context,WALLPAPER_DARK_BG,Color.Black.copy(alpha = 0.7f).toArgb()),
            getInt(context,WALLPAPER_DARK_FG,Color.White.toArgb()),
            getInt(context,WALLPAPER_TEXT_FG,Color.White.toArgb()),
            getInt(context,WALLPAPER_TEXT_BG,Color.Black.toArgb())
        )
    }
    fun getMonoThemedColors(context: Context) : WallpaperThemeColors {
        val isLightHour = isLightHour(context)
        val textFg = if (isLightHour) Color.Black.toArgb() else Color.White.toArgb()
        val textBg = Color.Transparent.toArgb()
        return WallpaperThemeColors(
            -1275068417,-16777216 ,  -1291845632 , -1,textFg,textBg
        )
    }
    fun getAppUpdatePromptUserCounter(context: Context): Int {
        var currentCounter = getInt(context,APP_UPDATE_PROMPT_COUNTER,0)
        if(shouldAppUpdatePromptUser(context)){
            currentCounter += 1
            setInt(context,APP_UPDATE_PROMPT_COUNTER,currentCounter)
        }
        return currentCounter
    }
    fun shouldAppUpdatePromptUser(context: Context): Boolean {
        val lastPromptTime = getLong(context,APP_UPDATE_PROMPT_TIME, default = 0L)
        val fortyEightHoursInMillis = 48 * 60 * 60 * 1000
        return (System.currentTimeMillis() - lastPromptTime) > fortyEightHoursInMillis
    }
    fun setAppUpdatePromptTime(context: Context, time: Long) {
        setLong(context,APP_UPDATE_PROMPT_TIME,time)
    }
    fun setThemedIcons(context: Context,enabled: Boolean){
        setBoolean(context,THEMED_ICONS,enabled)
    }
    fun getThemedIcons(context: Context): Boolean {
        return if(isBatterySaver(context)){
            false
        }  else {
            getBoolean(context,THEMED_ICONS,false)
        }
    }
    fun setAlphabeticalOrder(context: Context,enabled: Boolean){
        setBoolean(context,ALPHABETICAL_ORDER,enabled)
    }
    fun getAlphabeticalOrder(context: Context):Boolean{
        return getBoolean(context,ALPHABETICAL_ORDER,false)
    }
    fun getBackgroundImageUri(context: Context): Uri? {
        val uriString = getString(context,WALLPAPER_URI,null)
        return uriString?.toUri()
    }
    fun setBackgroundImageUri(context: Context, uri: Uri) {
        setString(context,WALLPAPER_URI,uri.toString())
    }
    fun setAppliedWallpaperImage(context: Context,image: String?){
        setString(context,APPLIED_WALLPAPER_IMAGE,image)
    }
    fun getAppliedWallpaperImage(context:Context): String? {
        return getString(context,APPLIED_WALLPAPER_IMAGE,null)
    }
    fun getWallpaperDirectory(context: Context): String?{
        return getString(context,WALLPAPER_DIRECTORY,null)
    }
    fun setWallpaperDirectory(context: Context,directoryUri: String?){
        setString(context,WALLPAPER_DIRECTORY,directoryUri)
    }
    fun getWallpaperFrequency(context: Context):String {
        return getString(context,WALLPAPER_FREQUENCY,"Hourly") ?: "Hourly"
    }
    fun setWallpaperFrequency(context: Context,frequency:String){
        setString(context,WALLPAPER_FREQUENCY,frequency)
    }
    fun setQuickAppsLayout(context: Context,layout:String){
        setString(context,QUICK_APPS_LAYOUT,layout)
    }
    fun getQuickAppsLayout(context: Context):String {
        return getString(context,QUICK_APPS_LAYOUT,"circular") ?: "circular"
    }
    fun setAppDrawerLayout(context: Context,layout:String){
        setString(context,APP_DRAWER_LAYOUT,layout)
    }
    fun getAppDrawerLayout(context: Context):String {
        return getString(context,APP_DRAWER_LAYOUT,"circular") ?: "circular"
    }
    fun setCustomWidgets(context: Context, enabled: Boolean){
        setBoolean(context,CUSTOM_WIDGETS,enabled)
    }
    fun getCustomWidgets(context: Context):Boolean{
        return getBoolean(context,CUSTOM_WIDGETS,false)
    }

    fun setWallpaperMotion(context: Context, enabled: Boolean) {
        setBoolean(context,KEY_WALLPAPER_MOTION,enabled)
    }
    fun getAutoWallpapers(context: Context, default: Boolean = true): Boolean {
        return if(isBatterySaver(context)){ false } else { getBoolean(context,AUTO_WALLPAPER,default) }
    }
    fun setAutoWallpapers(context: Context, enabled: Boolean) {
        setBoolean(context,AUTO_WALLPAPER,enabled)
    }
    fun getMonochrome(context: Context, default: Boolean = false): Boolean {
        return if (isBatterySaver(context)) true else getBoolean(context,MONOCHROME,default)
    }
    fun setMonochrome(context: Context, enabled: Boolean) {
        setBoolean(context,MONOCHROME,enabled)
    }
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
    fun getIconSize(context: Context, default: Int = 48): Int {
        return getInt(context,KEY_ICON_SIZE,default)
    }
    fun increaseAppListVersion(context: Context){
        val currentVersion = getAppListVersion(context)
        setInt(context,APP_LIST_UPDATE_COUNTER,currentVersion + 1)
    }
    fun getAppListVersion(context: Context): Int{
        return getInt(context,APP_LIST_UPDATE_COUNTER,0)
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

    fun setIconPack(context: Context,pack: String?){
        setString(context,ICON_PACK_PACKAGE,pack)
    }
    fun getIconPack(context: Context): String? {
        return getString(context,ICON_PACK_PACKAGE,null)
    }

    fun getBackgroundImagePath(context: Context): String? {
        if(getMonochrome(context,false)){
            val fileBlack = File(context.filesDir, "comfer_black.jpg")
            val fileWhite = File(context.filesDir, "comfer_white.jpg")
            return if(isLightHour(context)){
                fileWhite.absolutePath
            } else {
                fileBlack.absolutePath
            }
        }
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
        val previousImageData:ImageData? = getImageData(context)
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
        val existingHour = getInt(context, "now_hour", 0)
        return if (existingHour != hour) {
            hour
        } else {
            0
        }
    }
    fun setHour(context: Context,hour:Int){
        setInt(context, "now_hour", hour)
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
    fun getKeyboardLocale(context: Context): Locale {
        // 1. Get the raw preference (default to "system" if not set)
        val savedTag = getString(context, KEYBOARD_LOCALE, "system")
        // 2. If user selected a specific language manually, return it
        if (savedTag != null && savedTag != "system") {
            return Locale.forLanguageTag(savedTag)
        }
        // 3. "Follow System" Logic:
        // Get the device's top preferred locale
        val deviceLocale = LocaleListCompat.getAdjustedDefault()[0] ?: Locale.ENGLISH
        val supportedLocales = KeyboardLocale.getSupportedLocales()
        // 4. Find the best match in your supported list
        // First try exact match (fr-FR == fr-FR)
        // Then try language match (fr-FR matches fr)
        val bestMatch = supportedLocales.firstOrNull { it == deviceLocale }
            ?: supportedLocales.firstOrNull { it.language == deviceLocale.language }
            ?: Locale.ENGLISH
        return bestMatch
    }
    fun setKeyboardLocale(context: Context,locale: Locale) {
        setString(context,KEYBOARD_LOCALE,locale.toLanguageTag())
    }
    fun setBatterySaver(context: Context,enabled:Boolean){
        setBoolean(context,BATTERY_SAVER_MODE,enabled)
    }
    fun isBatterySaver(context: Context): Boolean {
        return getBoolean(context,BATTERY_SAVER_MODE,false)
    }
    fun setTopBarVisible(context: Context,enabled: Boolean){
        setBoolean(context,TOP_BAR_VISIBLE,enabled)
    }
    fun isTopBarVisible(context: Context): Boolean {
        return getBoolean(context,TOP_BAR_VISIBLE,false);
    }
}
