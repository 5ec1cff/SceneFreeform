package five.ec1cff.scene_freeform.hook

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.normalClass
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.android.*
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import five.ec1cff.scene_freeform.*

object SystemUIHooker: YukiBaseHooker() {
    private var mEnabled = false
    private val systemContext by lazy { getSystemContext() }
    private val activityManager by lazy { systemContext.getSystemService(ActivityManager::class.java) }
    private val whiteList = listOf(
        "com.termux",
        "org.telegram.messenger",
        "com.tencent.mobileqq",
        "com.tencent.mm"
    )

    private fun checkFromNotification(): Boolean {
        val stackTrace = Exception().stackTrace
        return stackTrace.find { it.className.contains("StatusBarNotificationActivityStarter") } != null
    }

    private fun updateState(key: String, newValue: Boolean? = null) {
        mEnabled = newValue ?: prefs.getBoolean(key, false)
        loggerI(msg = "$key=$mEnabled")
    }

    override fun onHook() {
        val key = moduleAppResources.getString(R.string.notification_hook_key)
        dataChannel.wait<Boolean>(key) {
            updateState(key, it)
        }
        updateState(key)
        PendingIntentClass.hook {
            injectMember {
                method {
                    name = "sendAndReturnResult"
                    param(
                        ContextClass, // context
                        Integer.TYPE, // code
                        IntentClass, // intent
                        findClass("android.app.PendingIntent\$OnFinished").normalClass!!, // onFinished
                        HandlerClass, // handler
                        String::class.java, // requiredPermission
                        BundleClass // options
                    )
                }
                beforeHook {
                    if (!mEnabled) return@beforeHook
                    if (!checkFromNotification()) return@beforeHook
                    val creatorPackage = (instance as PendingIntent).creatorPackage
                    loggerD(msg = "notification pendingIntent for $creatorPackage")
                    if (creatorPackage !in whiteList) return@beforeHook
                    if (activityManager.getRunningTasks(1).firstOrNull()?.isHome() == true) return@beforeHook
                    args(6).set(injectFreeFormOptions(args[6] as? Bundle?))
                }
            }
        }
    }
}