package five.ec1cff.scene_freeform

import android.app.ActivityManager
import android.app.ActivityThread
import android.app.TaskInfoHidden
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle

// frameworks/base/core/java/android/app/WindowConfiguration.java
private const val ACTIVITY_TYPE_HOME = 2

fun getSystemContext(): Context = ActivityThread.currentActivityThread().systemContext

fun ActivityManager.RunningTaskInfo.isHome(): Boolean =
    (this as TaskInfoHidden).topActivityType == ACTIVITY_TYPE_HOME

fun ActivityManager.RunningTaskInfo.isPackage(name: String): Boolean {
    val packageName = this.topActivity?.packageName
    return packageName != null && packageName == name
}

fun injectFreeFormOptions(bundle: Bundle?): Bundle {
    val newBundle = bundle ?: Bundle()
    newBundle.putInt("android.activity.windowingMode", 5)
    return newBundle
}

fun Context.checkPackageExists(name: String): Boolean {
    try {
        packageManager.getPackageInfo(name, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        return false
    }
    return true
}
