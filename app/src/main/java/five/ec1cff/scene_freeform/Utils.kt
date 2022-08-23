package five.ec1cff.scene_freeform

import android.app.ActivityManager
import android.app.ActivityThread
import android.app.TaskInfoHidden
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

fun Any?.getField(name: String): Any? = kotlin.runCatching {
    return@runCatching XposedHelpers.getObjectField(this, name)
}.getOrNull()

fun Any?.callMethod(name: String, vararg args: Any?): Any? =
    XposedHelpers.callMethod(this, name, *args)

private val threadPool by lazy { Executors.newCachedThreadPool() }

suspend fun Context.getInstalledPackagesAsync(flags: Int): List<PackageInfo> = suspendCoroutine {
    threadPool.submit {
        it.resume(packageManager.getInstalledPackages(flags))
    }
}

suspend fun Context.checkPackageExistsAsync(name: String): Boolean = suspendCoroutine {
    threadPool.submit {
        try {
            packageManager.getPackageInfo(name, 0)
            it.resume(true)
        } catch (e: PackageManager.NameNotFoundException) {
            it.resume(false)
        } catch (t: Throwable) {
            it.resumeWithException(t)
        }
    }
}
