package five.ec1cff.scene_freeform

import android.app.ActivityManager
import android.app.ActivityThread
import android.app.TaskInfoHidden
import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import java.lang.Thread.sleep
import kotlin.concurrent.thread

// frameworks/base/core/java/android/app/WindowConfiguration.java
private const val ACTIVITY_TYPE_HOME = 2

fun getSystemContext(): Context = ActivityThread.currentActivityThread().systemContext

fun ActivityManager.RunningTaskInfo.isHome(): Boolean = (this as TaskInfoHidden).topActivityType == ACTIVITY_TYPE_HOME

fun waitForService(name: String, interval: Long = 1000L, retry: Int = 100): Boolean {
    var i = retry
    val startTime = System.currentTimeMillis()
    while (i > 0) {
        if (ServiceManager.getService(name) != null) {
            val endTime = System.currentTimeMillis()
            loggerD(msg = "Wait $name for ${endTime - startTime} milliseconds!")
            return true
        }
        sleep(interval)
        i -= 1
    }
    val endTime = System.currentTimeMillis()
    loggerE(msg = "failed to wait for $name in ${endTime - startTime} milliseconds!")
    return false
}

fun runOnSystemStart(cb: () -> Unit) {
    thread(name = "system-waiter") {
        if (waitForService("activity") &&
            waitForService("content") &&
            waitForService("package")
        ) {
            cb.invoke()
        }
    }
}

fun injectFreeFormOptions(bundle: Bundle?): Bundle {
    val newBundle = bundle ?: Bundle()
    newBundle.putInt("android.activity.windowingMode", 5)
    return newBundle
}