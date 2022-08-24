@file:Suppress("Unchecked_cast", "deprecation")
package five.ec1cff.scene_freeform.hook.system
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.normalClass
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import five.ec1cff.scene_freeform.BuildConfig
import five.ec1cff.scene_freeform.bridge.CPBridge
import five.ec1cff.scene_freeform.bridge.IModuleApp
import five.ec1cff.scene_freeform.bridge.ISystemServer
import five.ec1cff.scene_freeform.config.Constants
import five.ec1cff.scene_freeform.config.Constants.FOO_VIEW_PACKAGE_NAME
import five.ec1cff.scene_freeform.config.Constants.QQ_PACKAGE_NAME
import five.ec1cff.scene_freeform.config.Constants.SYSTEM_PACKAGE
import five.ec1cff.scene_freeform.config.Constants.WECHAT_PACKAGE_NAME
import five.ec1cff.scene_freeform.getSystemContext
import five.ec1cff.scene_freeform.isHome
import five.ec1cff.scene_freeform.isPackage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object SystemServerHooker: YukiBaseHooker() {

    /**
     * 在这段时间内 App 启动自身 activity 被认为是小窗模式
     */
    private const val FREEFORM_PACKAGE_EXPIRES = 5000
    private val mContext by lazy { getSystemContext() }
    private val mActivityManager by lazy { mContext.getSystemService(ActivityManager::class.java) }
    private var mEnabled = false
    private var mShareEnabled = false
    private var mBrowserEnabled = false
    private var mSettingsEnabled = false
    private var mFileSelectorEnabled = false
    private var mQQShareEnabled = false
    private var mWeChatShareEnabled = false
    private var mLandscapeEnabled = false
    private var mIsLandscape = false
    private val SHARE_ACTIONS = listOf(
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE,
    )
    private val FILE_SELECTOR_ACTIONS = listOf(
        Intent.ACTION_GET_CONTENT,
        Intent.ACTION_OPEN_DOCUMENT
    )
    private val mBrowserComponents = mutableListOf<ComponentName>()
    private val mBlacklist = mutableSetOf<String>()
    private val executorService by lazy { Executors.newCachedThreadPool() }
    private var isSystemStarted = false

    /**
     * 记录被认为是小窗模式的 App
     */
    private val mFreeformPackages = ConcurrentHashMap<String, Long>()

    private val RESOLVER_COMPONENTS = listOf(
        ComponentName(SYSTEM_PACKAGE, "com.android.internal.app.ChooserActivity"),
        ComponentName(SYSTEM_PACKAGE, "com.android.internal.app.ResolverActivity"),
    )

    private val TARGET_PACKAGE_BLACKLIST = listOf(
        SYSTEM_PACKAGE,
        "com.android.camera"
    )

    private val WECHAT_SHARE_SDK_COMPONENTS = listOf(
        ComponentName(WECHAT_PACKAGE_NAME, "com.tencent.mm.plugin.base.stub.WXEntryActivity"), // Share activity of Wechat
    )

    fun needFixResult() = mFileSelectorEnabled || mQQShareEnabled

    private fun checkFreeformPackages(packageName: String?): Boolean {
        packageName ?: return false
        val time = mFreeformPackages[packageName] ?: return false
        return if (System.currentTimeMillis() < time) true
        else {
            removeFreeformPackage(packageName)
            false
        }
    }

    private fun addFreeformPackageInternal(packageName: String) {
        mFreeformPackages[packageName] = System.currentTimeMillis() + FREEFORM_PACKAGE_EXPIRES
    }

    private fun removeFreeformPackage(packageName: String) {
        mFreeformPackages.remove(packageName)
    }

    private fun checkCurrentTask(selfPackage: String, callingPackage: String?): Boolean {
        val id = Binder.clearCallingIdentity()
        val task = if (callingPackage != FOO_VIEW_PACKAGE_NAME) {
            mActivityManager.getRunningTasks(1).firstOrNull()
        } else {
            // dirty hack
            mActivityManager.getRunningTasks(2).getOrNull(1)
        }
        Binder.restoreCallingIdentity(id)
        val reason: String
        val result = if (task == null) {
            reason = "taskIsNull"
            false
        } else if (task.isHome()) {
            reason = "taskIsHome"
            false
        } else if (task.isPackage(selfPackage)) {
            reason = "taskIsSelf"
            false
        } else {
            reason = "pass"
            true
        }
        loggerD(msg = "checkCurrentTask result=$result,reason=$reason,topActivity=${task?.topActivity}")
        return result
    }

    fun shouldInject(
        intent: Intent,
        componentName: ComponentName,
        callingPackage: String?,
        record: ActivityRecord
    ): Boolean {
        if (intent.action == Intent.ACTION_MAIN) {
            return false
        }
        var reason = "unknown"
        val packageName = componentName.packageName
        val isInFreeformPackages = checkFreeformPackages(packageName)
        val isCallerSelf = packageName == callingPackage
        val result = if (isInFreeformPackages) {
            reason = "inFreeformPackages"
            isCallerSelf
        } else {
            val isMatch = if (isCallerSelf) {
                reason = "callSelfOrHome"
                false
            } else if (packageName in TARGET_PACKAGE_BLACKLIST) {
                reason = "packageInBlacklist"
                false
            } else if (packageName in mBlacklist) {
                reason = "packageInUserBlackList"
                false
            } else if (intent.component in RESOLVER_COMPONENTS) {
                reason = "componentIsResolver"
                false
            } else if (!checkCurrentTask(packageName, callingPackage)) {
                reason = "currentTask"
                false
            } else if (mLandscapeEnabled && mIsLandscape) {
                reason = "landscape"
                true
            } else if (mShareEnabled && intent.action in SHARE_ACTIONS) {
                reason = "share"
                true
            } else if (mBrowserEnabled && checkUri(intent)) {
                reason = "browser"
                true
            } else if (mFileSelectorEnabled && intent.action in FILE_SELECTOR_ACTIONS) {
                reason = "fileSelector"
                fixResultForActivity(record)
                true
            } else if (mWeChatShareEnabled && intent.component in WECHAT_SHARE_SDK_COMPONENTS) {
                reason = "wechatShare"
                true
            } else if (mQQShareEnabled && callingPackage != QQ_PACKAGE_NAME && checkQQShareSDK(intent)) {
                addFreeformPackageInternal(QQ_PACKAGE_NAME)
                fixResultForActivity(record, false)
                reason = "QQShare"
                true
            } else false
            if (isMatch) {
                addFreeformPackageInternal(packageName)
            }
            isMatch
        }
        loggerD(msg = "check freeform result=$result,reason=$reason,component=$componentName,caller=$callingPackage,isCallSelfOrHome=$isCallerSelf")
        return result
    }

    private fun checkQQShareSDK(intent: Intent): Boolean =
        intent.action == Intent.ACTION_VIEW && intent.scheme == "mqqapi" && intent.data?.authority == "share"

    private fun checkUri(intent: Intent): Boolean {
        if (intent.action == Intent.ACTION_VIEW) {
            val scheme = intent.data?.scheme
            // Open browser
            if (scheme == "http" || scheme == "https") return synchronized(mBrowserComponents) { intent.component in mBrowserComponents }
        }
        return false
    }

    private fun fixResultForActivity(record: ActivityRecord, needRedirectResult: Boolean = true) {
        FixActivityResultController.addForceNewTask(record, needRedirectResult)
        loggerD(msg = "fixResult:setNewTask ${record.hashCode()} (needRedirect=$needRedirectResult)")
    }

    private fun updateBrowserPackages(ctx: Context, reason: String? = null) {
        // DO NOT CALL pm.queryIntentActivities ON MAIN THREAD
        executorService.submit {
            kotlin.runCatching {
                synchronized(mBrowserComponents) {
                    mBrowserComponents.clear()
                    mBrowserComponents.addAll(
                        ctx.packageManager.queryIntentActivities(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
                            PackageManager.MATCH_ALL
                        ).map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                    )
                }
                loggerD(msg = "new mBrowserComponents($reason)=$mBrowserComponents")
            }.onFailure { loggerE(msg = "updateBrowserPackages($reason)", e = it) }
        }
    }

    @Synchronized
    private fun updateConfigInternal(bundle: Bundle) {
        (bundle.get(Constants.APP_JUMP) as? Boolean)?.also { mEnabled = it }
        (bundle.get(Constants.APP_JUMP_SHARE) as? Boolean)?.also { mShareEnabled = it }
        (bundle.get(Constants.APP_JUMP_BROWSER) as? Boolean)?.also { mBrowserEnabled = it }
        (bundle.get(Constants.APP_JUMP_FILE_SELECTOR) as? Boolean)?.also { mFileSelectorEnabled = it }
        (bundle.get(Constants.APP_JUMP_SETTINGS) as? Boolean)?.also { mSettingsEnabled = it }
        (bundle.get(Constants.HANDLE_QQ_SHARE) as? Boolean)?.also { mQQShareEnabled = it }
        (bundle.get(Constants.HANDLE_WECHAT_SHARE) as? Boolean)?.also { mWeChatShareEnabled = it }
        (bundle.get(Constants.LANDSCAPE) as? Boolean)?.also { mLandscapeEnabled = it }
        (bundle.get(Constants.APP_JUMP_SCOPE) as? ArrayList<String>)?.also {
            mBlacklist.clear()
            mBlacklist.addAll(it)
        }
        loggerD(msg = "mEnabled=$mEnabled, mBlacklist=$mBlacklist")
    }

    private object BinderHandler : ISystemServer.Stub() {
        override fun getVersion(): String = BuildConfig.VERSION_NAME

        override fun updateConfig(bundle: Bundle) {
            updateConfigInternal(bundle)
        }

        override fun requestReloadConfig() {
            reloadConfig()
        }

        override fun addFreeformPackage(name: String) {
            addFreeformPackageInternal(name)
        }

        override fun setPackageBlacklist(name: String, isBlacklist: Boolean) {
            synchronized(SystemServerHooker) {
                if (isBlacklist) mBlacklist.add(name)
                else mBlacklist.remove(name)
            }
        }

    }

    private fun reloadConfig() {
        executorService.submit {
            kotlin.runCatching {
                val moduleBridge = IModuleApp.Stub.asInterface(
                    CPBridge.getBinderFromBridge(mContext, BuildConfig.CONFIG_AUTHORITY)
                )
                val b = moduleBridge.requireConfig(BuildConfig.VERSION_NAME, SYSTEM_PACKAGE)
                updateConfigInternal(b)
            }.onFailure { loggerE(msg = "reloadConfigForSystem", e = it) }
        }
    }

    private fun onSystemStarted() {
        if (!isSystemStarted) {
            loggerD(msg = "Welcome to SceneFreeform!")
            reloadConfig()
            mContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val i = intent.getIntExtra("android.intent.extra.user_handle", -114514)
                    if (i == 0) updateBrowserPackages(ctx, "boot completed")
                }
            }, IntentFilter(Intent.ACTION_BOOT_COMPLETED))
            mContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent?) {
                    updateBrowserPackages(ctx, intent?.action)
                }
            }, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addDataScheme("package")
            })
            mContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, p1: Intent?) {
                    val orientation = ctx.resources.configuration.orientation
                    mIsLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
                }

            }, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
            isSystemStarted = true
        }
    }

    override fun onHook() {
        CPBridge.setBinderForBridge(BinderHandler.asBinder())
        val phaseBootCompleted = findClass("com.android.server.SystemService").normalClass!!.field {
            name = "PHASE_BOOT_COMPLETED"
        }.get(null).self
        "com.android.server.SystemServiceManager".hook {
            injectMember {
                method {
                    name = "startBootPhase"
                    paramCount(2)
                }
                afterHook {
                    if (args[1] == phaseBootCompleted)
                        onSystemStarted()
                }
            }
        }
        loadHooker(ActivityStarterHooker)
        loadHooker(ActivityRecordHooker)
    }
}
