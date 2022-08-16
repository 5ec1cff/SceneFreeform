@file:Suppress("Unchecked_cast", "deprecation")
package five.ec1cff.scene_freeform.hook
import android.app.ActivityManager
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.res.Configuration
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.normalClass
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.android.ConfigurationClass
import com.highcapable.yukihookapi.hook.type.android.IBinderClass
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringType
import de.robv.android.xposed.XposedHelpers
import five.ec1cff.scene_freeform.*
import five.ec1cff.scene_freeform.config.Constants
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.collections.HashSet

object SystemServerHooker: YukiBaseHooker() {
    private const val QQ_PACKAGE_NAME = "com.tencent.mobileqq"
    private const val FOO_VIEW_PACKAGE_NAME = "com.fooview.android.fooview"
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
    private val mResultTargetActivities = WeakHashMap<IBinder, Boolean>()
    /**
     *  ActivityRecord -> ActivityRecord.resultTo
     */
    private val mResultSourceActivities = WeakHashMap<Any, WeakReference<Any>>()

    /**
     * 记录被认为是小窗模式的 App
     */
    private val mFreeformPackages = ConcurrentHashMap<String, Long>()

    private val RESOLVER_COMPONENTS = listOf(
        ComponentName("android", "com.android.internal.app.ChooserActivity"),
        ComponentName("android", "com.android.internal.app.ResolverActivity"),
    )

    private val TARGET_PACKAGE_BLACKLIST = listOf(
        "android",
        "com.android.camera"
    )

    private val WECHAT_SHARE_SDK_COMPONENTS = listOf(
        ComponentName("com.tencent.mm", "com.tencent.mm.plugin.base.stub.WXEntryActivity"), // Share activity of Wechat
    )

    private fun needFixResult() = mFileSelectorEnabled || mQQShareEnabled

    private fun checkFreeformPackages(packageName: String?): Boolean {
        packageName ?: return false
        val time = mFreeformPackages[packageName] ?: return false
        return if (System.currentTimeMillis() < time) true
        else {
            removeFreeformPackage(packageName)
            false
        }
    }

    private fun addFreeformPackage(packageName: String) {
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

    private fun shouldInject(intent: Intent, callingPackage: String?, userId: Int, resultTo: IBinder? = null): Boolean {
        if (intent.action == Intent.ACTION_MAIN) {
            return false
        }
        var reason = "unknown"
        var result = false
        var isCallerSelf = false
        val packageName = intent.component?.packageName
        if (intent.component == null) {
            if (mQQShareEnabled && callingPackage != QQ_PACKAGE_NAME && checkQQShareSDK(intent)) {
                result = true
                addFreeformPackage(QQ_PACKAGE_NAME)
                if (resultTo != null) fixResultForActivity(resultTo, false)
                reason = "QQShare"
            } else if (mFileSelectorEnabled && intent.action in FILE_SELECTOR_ACTIONS) {
                val pm = mContext.packageManager as PackageManagerHidden
                val component = pm.resolveActivityAsUser(intent, 0, userId).activityInfo
                    .let { ComponentName(it.packageName, it.name) }
                if (component !in RESOLVER_COMPONENTS) {
                    if (resultTo != null) fixResultForActivity(resultTo)
                    reason = "fileSelectorSingle"
                    result = true
                }
            }
        } else if (packageName != null) {
            val isInFreeformPackages = checkFreeformPackages(packageName)
            isCallerSelf = (callingPackage != null && packageName == callingPackage)
            result = if (isInFreeformPackages) {
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
                    if (resultTo != null) fixResultForActivity(resultTo)
                    true
                } else if (mWeChatShareEnabled && intent.component in WECHAT_SHARE_SDK_COMPONENTS) {
                    reason = "wechatShare"
                    true
                } else false
                if (isMatch) {
                    addFreeformPackage(packageName)
                }
                isMatch
            }
        } else {
            reason = "unknownPackageName"
        }
        loggerD(msg = "check freeform result=$result,reason=$reason,package=$packageName,caller=$callingPackage,result=$result,isCallSelfOrHome=$isCallerSelf")
        return result
    }

    private fun checkCaller(packageName: String?, user: Int): Boolean {
        kotlin.runCatching {
            if (packageName == null) return false
            val callingUid = Binder.getCallingUid()
            if (callingUid == 0 || callingUid == 1000 || callingUid == 2000) return true
            // may cause NameNotFoundException
            // i.e. `PackageManagerServiceInjector.verifyInstallFromShell` with packageName == "pm"
            val pm = mContext.packageManager as PackageManagerHidden
            if (pm.getPackageUidAsUser(packageName, user) != callingUid) {
                loggerW(msg = "caller package $packageName does not match $callingUid (in user $user)")
                return false
            }
            return true
        }
        return false
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

    private fun fixResultForActivity(resultTo: IBinder, needRedirectResult: Boolean = true) {
        synchronized(mResultTargetActivities) {
            mResultTargetActivities[resultTo] = needRedirectResult
            loggerD(msg = "bypass for ${System.identityHashCode(resultTo)}")
        }
    }

    private fun HookParam.beforeHookCommon(
        intentIndex: Int = 3,
        optionsIndex: Int = 10,
        shouldCheckCaller: Boolean = true,
        callingPackageIndex: Int = 1,
        userIdIndex: Int = 11,
        resultToIndex: Int = 5
    ) {
        if (!mEnabled) return
        val callingPackage = args[callingPackageIndex] as? String?
        val userId = args[userIdIndex] as? Int ?: return
        val resultTo = args[resultToIndex] as? IBinder?
        if (shouldCheckCaller && !checkCaller(callingPackage, userId)) return
        val intent = args[intentIndex] as? Intent ?: return
        if (!shouldInject(intent, callingPackage, userId, resultTo)) return
        intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK
        args(optionsIndex).set(injectFreeFormOptions(args[optionsIndex] as? Bundle?))
    }

    private fun updateBrowserPackages(ctx: Context) {
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
                loggerD(msg = "new mBrowserComponents=$mBrowserComponents")
            }.onFailure { loggerE(msg = "updateBrowserPackages", e = it) }
        }
    }

    override fun onHook() {
        onAppLifecycle {
            onConfigurationChanged { _, config ->
                val orientation = config.orientation
                mIsLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            registerReceiver(Intent.ACTION_BOOT_COMPLETED) { ctx, intent ->
                val i = intent.getIntExtra("android.intent.extra.user_handle", -114514)
                loggerD(msg = "boot completed:$i")
                if (!isSystemStarted && i == 0) {
                    updateBrowserPackages(ctx)
                    ctx.registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent?) {
                            loggerD(msg = "package monitor:$intent")
                            updateBrowserPackages(ctx)
                        }
                    }, IntentFilter().apply {
                        addAction(Intent.ACTION_PACKAGE_ADDED)
                        addAction(Intent.ACTION_PACKAGE_CHANGED)
                        addAction(Intent.ACTION_PACKAGE_REMOVED)
                        addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                        addDataScheme("package")
                    })
                    isSystemStarted = true
                }
            }
            // for systemui
            registerReceiver(Constants.ACTION_ADD_FREEFORM_PACKAGE) { _, intent ->
                intent.getStringExtra(Constants.EXTRA_PACKAGE)?.let {
                    addFreeformPackage(it)
                    loggerD(msg = "add freeform package $it from broadcast")
                }
            }
        }
        dataChannel.wait(Constants.CHANNEL_DATA_GET_VERSION_SS) {
            dataChannel.put(Constants.CHANNEL_DATA_GET_VERSION_SS, BuildConfig.VERSION_NAME)
            loggerD(msg = "received get version from $it")
        }
        dataChannel.wait(Constants.CHANNEL_DATA_UPDATE_CONFIG) { bundle ->
            (bundle.get(Constants.APP_JUMP) as? Boolean)?.also { mEnabled = it }
            (bundle.get(Constants.APP_JUMP_SHARE) as? Boolean)?.also { mShareEnabled = it }
            (bundle.get(Constants.APP_JUMP_BROWSER) as? Boolean)?.also { mBrowserEnabled = it }
            (bundle.get(Constants.APP_JUMP_FILE_SELECTOR) as? Boolean)?.also { mFileSelectorEnabled = it }
            (bundle.get(Constants.APP_JUMP_SETTINGS) as? Boolean)?.also { mSettingsEnabled = it }
            (bundle.get(Constants.HANDLE_QQ_SHARE) as? Boolean)?.also { mQQShareEnabled = it }
            (bundle.get(Constants.HANDLE_WECHAT_SHARE) as? Boolean)?.also { mWeChatShareEnabled = it }
            (bundle.get(Constants.LANDSCAPE) as? Boolean)?.also { mLandscapeEnabled = it }
            (bundle.get(Constants.APP_JUMP_SCOPE) as? HashSet<String>)?.also {
                mBlacklist.clear()
                mBlacklist.addAll(it)
            }
            loggerD(msg = "mEnabled=$mEnabled, mBlacklist=$mBlacklist")
        }
        mEnabled = prefs.getBoolean(Constants.APP_JUMP, false)
        mShareEnabled = prefs.getBoolean(Constants.APP_JUMP_SHARE, false)
        mBrowserEnabled = prefs.getBoolean(Constants.APP_JUMP_BROWSER, false)
        mFileSelectorEnabled = prefs.getBoolean(Constants.APP_JUMP_FILE_SELECTOR, false)
        mSettingsEnabled = prefs.getBoolean(Constants.APP_JUMP_SETTINGS, false)
        mQQShareEnabled = prefs.getBoolean(Constants.HANDLE_QQ_SHARE, false)
        mWeChatShareEnabled = prefs.getBoolean(Constants.HANDLE_WECHAT_SHARE, false)
        mLandscapeEnabled = prefs.getBoolean(Constants.LANDSCAPE, false)
        mBlacklist.addAll(prefs.getStringSet(Constants.APP_JUMP_SCOPE, setOf()))
        loggerD(msg = "mEnabled=$mEnabled, mBlacklist=$mBlacklist")
        val classIApplicationThread = findClass("android.app.IApplicationThread").normalClass!!
        val classProfilerInfo = findClass("android.app.ProfilerInfo").normalClass!!
        // TODO: hook ActivityStarter
        "com.android.server.wm.ActivityTaskManagerService".hook {
            injectMember {
                method {
                    name = "startActivityAsUser"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        param(
                            classIApplicationThread, // caller
                            StringType, // callingPackage 1
                            StringType, // callingFeatureId
                            IntentClass, // intent 3
                            StringType, // resolvedType
                            IBinderClass, // resultTo 5
                            StringType, // resultWho
                            IntType, // requestCode
                            IntType, // startFlags
                            classProfilerInfo, // ProfilerInfo
                            BundleClass, // bOptions
                            IntType, // userId 11
                            BooleanType // validateIncomingUser
                        )
                    } else {
                        param(
                            classIApplicationThread, // caller
                            StringType, // callingPackage 1
                            IntentClass, // intent 2
                            StringType, // resolvedType
                            IBinderClass, // resultTo
                            StringType, // resultWho
                            IntType, // requestCode
                            IntType, // startFlags
                            classProfilerInfo, // ProfilerInfo
                            BundleClass, // bOptions
                            IntType, // userId
                            BooleanType // validateIncomingUser
                        )
                    }
                }
                beforeHook {
                    this.beforeHookCommon()
                }
            }
            injectMember {
                method {
                    name = "startActivityAsCaller"
                    param(
                        classIApplicationThread, // caller
                        StringType, // callingPackage
                        IntentClass, // intent 2
                        StringType, // resolvedType
                        IBinderClass, // resultTo 4
                        StringType, // resultWho
                        IntType, // requestCode
                        IntType, // flags
                        classProfilerInfo, // ProfilerInfo
                        BundleClass, // options
                        IBinderClass, // permissionToken
                        BooleanType, // ignoreTargetSecurity
                        IntType // userId 12
                    )
                }
                beforeHook {
                    this.beforeHookCommon(intentIndex = 2, optionsIndex = 9, shouldCheckCaller = false, userIdIndex = 12, resultToIndex = 4)
                }
            }
            injectMember {
                method {
                    name = "startActivityAndWait"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        param(
                            classIApplicationThread, // caller
                            StringType, // callingPackage 1
                            StringType, // callingFeatureId
                            IntentClass, // intent 3
                            StringType, // resolvedType
                            IBinderClass, // resultTo 5
                            StringType, // resultWho
                            IntType, // requestCode
                            IntType, // flags
                            classProfilerInfo, // ProfilerInfo
                            BundleClass, // options
                            IntType // userId 11
                        )
                    } else {
                        param(
                            classIApplicationThread, // caller
                            StringType, // callingPackage
                            IntentClass, // intent
                            StringType, // resolvedType
                            IBinderClass, // resultTo
                            StringType, // resultWho
                            IntType, // requestCode
                            IntType, // flags
                            classProfilerInfo, // ProfilerInfo
                            BundleClass, // options
                            IntType // userId
                        )
                    }
                }
                beforeHook {
                    this.beforeHookCommon()
                }
            }
            injectMember {
                method {
                    name = "startActivityWithConfig"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        param(
                            classIApplicationThread, // caller
                            StringType, // callingPackage 1
                            StringType, // callingFeatureId
                            IntentClass, // intent 3
                            StringType, // resolvedType
                            IBinderClass, // resultTo 5
                            StringType, // resultWho
                            IntType, // requestCode
                            IntType, // startFlags
                            ConfigurationClass, // newConfig
                            BundleClass, // options
                            IntType // userId 11
                        )
                    } else {
                        param(
                            classIApplicationThread, // caller
                            StringType, // callingPackage
                            IntentClass, // intent
                            StringType, // resolvedType
                            IBinderClass, // resultTo
                            StringType, // resultWho
                            IntType, // requestCode
                            IntType, // startFlags
                            ConfigurationClass, // newConfig
                            BundleClass, // options
                            IntType // userId
                        )
                    }
                }
                beforeHook {
                    this.beforeHookCommon()
                }
            }
        }
        // API >= 30
        "com.android.server.wm.ActivityStarter".hook {
            injectMember {
                method {
                    name = "sendNewTaskResultRequestIfNeeded"
                    emptyParam()
                }
                beforeHook {
                    if (!needFixResult()) return@beforeHook
                    val resultTo = instance.getField("mRequest")?.getField("resultTo") ?: return@beforeHook
                    synchronized(mResultTargetActivities) {
                        if (resultTo in mResultTargetActivities) {
                            loggerD(msg = "Bypass NEW_TASK intent result restriction for ${System.identityHashCode(resultTo)}")
                            resultNull()
                        }
                    }
                }
            }
            injectMember {
                method {
                    name = "computeTargetTask"
                    emptyParam()
                }
                beforeHook {
                    if (!needFixResult()) return@beforeHook
                    val resultTo = instance.getField("mRequest")?.getField("resultTo") ?: return@beforeHook
                    synchronized(mResultTargetActivities) {
                        val needRedirectResult = mResultTargetActivities[resultTo] ?: return@beforeHook
                        loggerD(msg = "Force new task for ${System.identityHashCode(resultTo)}")
                        resultNull()
                        mResultTargetActivities.remove(resultTo)
                        if (needRedirectResult) {
                            val record = instance.getField("mStartActivity") ?: return@beforeHook
                            val resultToRecord = record.getField("resultTo") ?: return@beforeHook
                            mResultSourceActivities[record] = WeakReference(resultToRecord)
                        }
                    }
                }
            }
        }
        val ACTIVITY_RESUMED = findClass("com.android.server.wm.ActivityStack\$ActivityState")
            .normalClass!!.field { name = "RESUMED" }.get().self
        "com.android.server.wm.ActivityRecord".hook {
            injectMember {
                method {
                    name = "finishActivityResults"
                }
                beforeHook {
                    if (!needFixResult()) return@beforeHook
                    val fromRecord = instance
                    val toRecord = mResultSourceActivities[fromRecord]?.get()
                    if (toRecord != null && toRecord.callMethod("isState", ACTIVITY_RESUMED) == true) {
                        resultNull()
                        val activityInfo = fromRecord.getField("info") as? ActivityInfo?
                        val callingUid = activityInfo?.applicationInfo?.uid ?: 1000 /* callingUid */
                        val resultWho = fromRecord.getField("resultWho") /* resultWho */
                        val requestCode = fromRecord.getField("requestCode") /* requestCode */
                        val resultCode = args[0] /* resultCode */
                        val data = args[1] /* data */
                        val dataGrants = args[2] /* dataGrants */
                        loggerD(msg = "callingUid=$callingUid,resultWho=$resultWho,requestCode=$requestCode,resultCode=$resultCode,data=$data,dataGrants=$dataGrants")
                        toRecord.callMethod(
                            "sendResult",
                            callingUid,
                            resultWho,
                            requestCode,
                            resultCode,
                            data,
                            dataGrants
                        )
                    }
                    mResultSourceActivities.remove(fromRecord)
                }
            }
        }
    }
}
