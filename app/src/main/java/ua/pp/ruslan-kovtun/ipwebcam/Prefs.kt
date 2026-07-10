package ua.pp.ruslan_kovtun.ipwebcam

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var width: Int
        get() = prefs.getInt(KEY_WIDTH, DEFAULT_WIDTH)
        set(value) = prefs.edit().putInt(KEY_WIDTH, value).apply()

    var height: Int
        get() = prefs.getInt(KEY_HEIGHT, DEFAULT_HEIGHT)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value).apply()

    var fps: Int
        get() = prefs.getInt(KEY_FPS, DEFAULT_FPS)
        set(value) = prefs.edit().putInt(KEY_FPS, value).apply()

    companion object {
        private const val PREFS_NAME = "ipwebcam_prefs"
        private const val KEY_PORT = "port"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_FPS = "fps"

        const val DEFAULT_PORT = 8080
        const val DEFAULT_WIDTH = 640
        const val DEFAULT_HEIGHT = 480
        const val DEFAULT_FPS = 15
    }
}
