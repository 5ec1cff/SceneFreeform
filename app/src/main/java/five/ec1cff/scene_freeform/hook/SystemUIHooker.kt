package five.ec1cff.scene_freeform.hook

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.*
import android.content.res.Configuration
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.normalClass
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.type.android.*
import com.highcapable.yukihookapi.hook.type.java.StringType
import five.ec1cff.scene_freeform.*
import five.ec1cff.scene_freeform.config.Constants
import java.util.*
import kotlin.collections.HashSet

object SystemUIHooker: YukiBaseHooker() {
    private var mEnabled = false
    private val systemContext by lazy { getSystemContext() }
    private val activityManager by lazy { systemContext.getSystemService(ActivityManager::class.java) }
    private val mFreeformPendingIntent = WeakHashMap<PendingIntent, Pair<Boolean, String>>()
    private val whiteList = mutableSetOf<String>()
    private var mLandscapeEnabled = false
    private var mIsLandscape = false
    private var mSystemUIStarted = false

    override fun onHook() {
        onAppLifecycle {
            onCreate {
                if (this.packageName == Constants.SYSTEM_UI_PACKAGE) {
                    if (!mSystemUIStarted) {
                        mIsLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context, p1: Intent?) {
                                    val config = ctx.resources.configuration
                                    val orientation = config.orientation
                                    mIsLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
                                    loggerD(msg = "orientation: $orientation")
                                }
                        }, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
                        mSystemUIStarted = true
                        loggerD(msg = "systemui started")
                    }
                }
            }
        }
        dataChannel.wait(Constants.CHANNEL_DATA_GET_VERSION_SU) {
            dataChannel.put(Constants.CHANNEL_DATA_GET_VERSION_SU, BuildConfig.VERSION_NAME)
        }
        dataChannel.wait(Constants.CHANNEL_DATA_UPDATE_CONFIG) { bundle ->
            (bundle.get(Constants.NOTIFICATION) as? Boolean)?.also { mEnabled = it }
            (bundle.get(Constants.LANDSCAPE) as? Boolean)?.also { mLandscapeEnabled = it }
            (bundle.get(Constants.NOTIFICATION_SCOPE) as? HashSet<String>)?.also {
                whiteList.clear()
                whiteList.addAll(it)
            }
            loggerD(msg = "mEnabled=$mEnabled, scope=$whiteList")
        }
        prefs.apply {
            mEnabled = getBoolean(Constants.NOTIFICATION, false)
            mLandscapeEnabled = getBoolean(Constants.LANDSCAPE, false)
            whiteList.addAll(getStringSet(Constants.NOTIFICATION_SCOPE, setOf()))
            loggerD(msg = "mEnabled=$mEnabled, mWhiteList=$whiteList")
        }
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
                    val packageName = statusBarNotification.packageName
                    val intent = notification.contentIntent ?: notification.fullScreenIntent ?: return@beforeHook
                    synchronized(mFreeformPendingIntent) {
                        mFreeformPendingIntent[intent] = if (mEnabled && packageName in whiteList) {
                            loggerW(msg = "launch $packageName as freeform from notification")
                            systemContext.sendBroadcast(
                                Intent(Constants.ACTION_ADD_FREEFORM_PACKAGE).apply {
                                    setPackage(Constants.SYSTEM_SERVER_PACKAGE)
                                    putExtra(Constants.EXTRA_PACKAGE, packageName)
                                }
                            )
                            Pair(true, packageName)
                        } else Pair(false, packageName)
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
                        StringType, // requiredPermission
                        BundleClass // options
                    )
                }
                beforeHook {
                    var shouldInject = false
                    val intent = instance as PendingIntent
                    val record = synchronized(mFreeformPendingIntent) {
                        mFreeformPendingIntent[intent] ?: return@beforeHook
                    }
                    var reason = "unknown"
                    val pkgName = record.second
                    if (mLandscapeEnabled && mIsLandscape) {
                        reason = "landscape"
                        shouldInject = true
                    } else if (mEnabled) {
                        shouldInject = record.first
                        reason = "scope"
                    }
                    shouldInject = shouldInject && !(activityManager.getRunningTasks(1)
                        .firstOrNull()?.let { it.isHome() || it.isPackage(pkgName) } == true)
                        .also { if (!it) reason = "taskIsHomeOrSelf" }
                    if (shouldInject) {
                        args(6).set(injectFreeFormOptions(args[6] as? Bundle?))
                    }
                    loggerD(msg = "launch $pkgName from notification shouldInject=$shouldInject,reason=$reason")
                }
            }
        }
    }
}