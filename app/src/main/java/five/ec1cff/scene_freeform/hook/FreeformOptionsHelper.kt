package five.ec1cff.scene_freeform.hook

import android.app.ActivityOptions
import android.app.ActivityOptionsHidden
import android.os.Bundle

@Suppress("CAST_NEVER_SUCCEEDS", "SoonBlockedPrivateApi", "BlockedPrivateApi", "PrivateApi")
object FreeformOptionsHelper {
    private val KEY_LAUNCH_WINDOWING_MODE by lazy {
        kotlin.runCatching {
            ActivityOptions::class.java.getDeclaredField("KEY_LAUNCH_WINDOWING_MODE")
                .also { it.isAccessible = true }
                .get(null) as String
        }.getOrNull() ?: "android.activity.windowingMode"
    }
    private val WINDOWING_MODE_FREEFORM by lazy {
        kotlin.runCatching {
            Class.forName("android.app.WindowConfiguration")
                .getDeclaredField("WINDOWING_MODE_FREEFORM")
                .also { it.isAccessible = true }
                .get(null) as Int
        }.getOrNull() ?: 5
    }

    fun ActivityOptions?.injectFreeformOptions(): ActivityOptions =
        (this ?: ActivityOptions.makeBasic())
            .apply {
                this as ActivityOptionsHidden
                setLaunchWindowingMode(WINDOWING_MODE_FREEFORM)
            }

    fun Bundle?.injectFreeformOptions(): Bundle = (this ?: Bundle()).apply {
        putInt(KEY_LAUNCH_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)
    }
}