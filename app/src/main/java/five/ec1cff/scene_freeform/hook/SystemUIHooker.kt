package five.ec1cff.scene_freeform.hook

import android.app.ActivityManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.normalClass
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.type.android.*
import com.highcapable.yukihookapi.hook.type.java.StringType
import five.ec1cff.scene_freeform.*
import five.ec1cff.scene_freeform.bridge.CPBridge
import five.ec1cff.scene_freeform.bridge.IModuleApp
import five.ec1cff.scene_freeform.bridge.ISystemServer
import five.ec1cff.scene_freeform.bridge.ISystemUI
import five.ec1cff.scene_freeform.config.Constants
import java.util.*
import java.util.concurrent.Executors

object SystemUIHooker: YukiBaseHooker() {
    private var mEnabled = false
    private val systemContext by lazy { getSystemContext() }
    private lateinit var mApp: Application
    private val activityManager by lazy { systemContext.getSystemService(ActivityManager::class.java) }
    private val mFreeformPendingIntent = WeakHashMap<PendingIntent, Pair<Boolean, String>>()
    private val whiteList = mutableSetOf<String>()
    private var mLandscapeEnabled = false
    private var mIsLandscape = false
    private var mSystemUIStarted = false
    private var systemBridge: ISystemServer? = null
    private val executorService by lazy { Executors.newCachedThreadPool() }

    @Synchronized
    private fun updateConfigInternal(bundle: Bundle) {
        (bundle.get(Constants.NOTIFICATION) as? Boolean)?.also { mEnabled = it }
        (bundle.get(Constants.LANDSCAPE) as? Boolean)?.also { mLandscapeEnabled = it }
        (bundle.get(Constants.NOTIFICATION_SCOPE) as? ArrayList<String>)?.also {
            whiteList.clear()
            whiteList.addAll(it)
        }
        loggerD(msg = "mEnabled=$mEnabled, scope=$whiteList")
    }

    internal object BinderHandler : ISystemUI.Stub() {
        override fun getVersion(): String = BuildConfig.VERSION_NAME

        override fun updateConfig(bundle: Bundle) {
            updateConfigInternal(bundle)
        }

        override fun requestReloadConfig() {
            reloadConfig()
        }

        override fun setPackageWhitelist(name: String, isWhitelist: Boolean) {
            synchronized(SystemUIHooker) {
                if (isWhitelist) whiteList.add(name)
                else whiteList.remove(name)
            }
        }

    }

    private fun reloadConfig() {
        executorService.submit {
            kotlin.runCatching {
                val moduleBridge = IModuleApp.Stub.asInterface(
                    CPBridge.getBinderFromBridge(mApp, BuildConfig.CONFIG_AUTHORITY)
                )
                val b = moduleBridge.requireConfig(BuildConfig.VERSION_NAME,
                    Constants.SYSTEM_UI_PACKAGE
                )
                updateConfigInternal(b)
            }.onFailure { loggerE(msg = "initiateConfigForSystemUI", e = it) }
        }
    }

    override fun onHook() {
        CPBridge.setBinderForBridge(BinderHandler.asBinder())
        onAppLifecycle {
            onCreate {
                if (this.packageName == Constants.SYSTEM_UI_PACKAGE) {
                    if (!mSystemUIStarted) {
                        mApp = this
                        mIsLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context, p1: Intent?) {
                                    val config = ctx.resources.configuration
                                    val orientation = config.orientation
                                    mIsLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
                                }
                        }, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
                        reloadConfig()
                        executorService.submit {
                            kotlin.runCatching {
                                systemBridge = ISystemServer.Stub.asInterface(
                                    CPBridge.getBinderForSystem(this)
                                )
                            }.onFailure { loggerE(msg = "getSystemBridgeInSystemUI", e = it) }
                        }
                        mSystemUIStarted = true
                        loggerD(msg = "systemui started")
                    }
                }
            }
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
                    val shouldInject = mEnabled && packageName in whiteList &&
                            activityManager.getRunningTasks(1)
                                .firstOrNull()
                                ?.let { it.isHome() || it.isPackage(packageName) } != true
                    synchronized(mFreeformPendingIntent) {
                        mFreeformPendingIntent[intent] = if (shouldInject) {
                            loggerW(msg = "launch $packageName as freeform from notification")
                            kotlin.runCatching {
                                systemBridge?.addFreeformPackage(packageName)
                            }
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
                    if (shouldInject) {
                        args(6).set(injectFreeFormOptions(args[6] as? Bundle?))
                    }
                    loggerD(msg = "launch $pkgName from notification shouldInject=$shouldInject,reason=$reason")
                }
            }
        }
    }
}