package five.ec1cff.scene_freeform.hook

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
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
import java.util.*

object SystemUIHooker: YukiBaseHooker() {
    private var mEnabled = false
    private val systemContext by lazy { getSystemContext() }
    private val activityManager by lazy { systemContext.getSystemService(ActivityManager::class.java) }
    private val mFreeformPendingIntent = WeakHashMap<PendingIntent, Boolean>()
    private val whiteList = listOf(
        "com.termux",
        "org.telegram.messenger",
        "com.tencent.mobileqq",
        "com.tencent.mm"
    )

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
        "com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter".hook {
            injectMember {
                method {
                    name = "onNotificationClicked"
                    param(
                        StatusBarNotificationClass,
                        findClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow").normalClass!!
                    )
                }
                beforeHook {
                    val statusBarNotification = args[0] as StatusBarNotification
                    val notification = statusBarNotification.notification
                    loggerD(msg = "check notification statusBarNotification=$statusBarNotification notification=$notification")
                    val intent = notification.contentIntent ?: notification.fullScreenIntent ?: return@beforeHook
                    if (statusBarNotification.packageName in whiteList) {
                        synchronized(mFreeformPendingIntent) {
                            mFreeformPendingIntent[intent] = true
                        }
                        loggerW(msg = "mark $intent as freeform")
                    }
                }
            }
        }
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
                    val intent = instance as PendingIntent
                    synchronized(mFreeformPendingIntent) {
                        if (mFreeformPendingIntent[intent] != true) return@beforeHook
                    }
                    if (activityManager.getRunningTasks(1).firstOrNull()?.isHome() == true) return@beforeHook
                    loggerD(msg = "inject freeform options for $intent")
                    args(6).set(injectFreeFormOptions(args[6] as? Bundle?))
                }
            }
        }
    }
}