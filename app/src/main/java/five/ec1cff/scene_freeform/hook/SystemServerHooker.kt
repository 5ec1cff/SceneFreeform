package five.ec1cff.scene_freeform.hook

import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.normalClass
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.android.ConfigurationClass
import com.highcapable.yukihookapi.hook.type.android.IBinderClass
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import five.ec1cff.scene_freeform.R
import five.ec1cff.scene_freeform.getSystemContext
import five.ec1cff.scene_freeform.injectFreeFormOptions
import java.util.concurrent.ConcurrentHashMap

object SystemServerHooker: YukiBaseHooker() {
    private const val QQ_PACKAGE_NAME = "com.tencent.mobileqq"
    /**
     * 在这段时间内 App 启动自身 activity 被认为是小窗模式
     */
    private const val FREEFORM_PACKAGE_EXPIRES = 5000
    private val mContext by lazy { getSystemContext() }
    private var mEnabled = false
    private val ACTIONS = listOf(
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE,
        // Intent.ACTION_VIEW,
        // TODO: 以下两个对 documentsui 无效，原因未知
        // Intent.ACTION_GET_CONTENT,
        // Intent.ACTION_OPEN_DOCUMENT
    )

    /**
     * 记录被认为是小窗模式的 App
     */
    private val mFreeformPackages = ConcurrentHashMap<String, Long>()

    // TODO: configurable list

    private val TARGET_COMPONENT_BLACKLIST = listOf(
        ComponentName("android", "com.android.internal.app.ChooserActivity"),
        ComponentName("android", "com.android.internal.app.ResolverActivity"),
    )

    private val TARGET_PACKAGE_BLACKLIST = listOf(
        "android",
        "com.android.camera"
    )

    private val TARGET_COMPONENT_WHITELIST = listOf(
        ComponentName("com.tencent.mm", "com.tencent.mm.plugin.base.stub.WXEntryActivity"), // Share activity of Wechat
    )

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

    private fun shouldInject(intent: Intent, callingPackage: String?): Boolean {
        if (intent.action == Intent.ACTION_MAIN) {
            return false
        }
        if (intent.component == null) return callingPackage != QQ_PACKAGE_NAME && checkQQShareSDK(intent)
        val packageName = intent.component?.packageName ?: return false
        val isInFreeformPackages = checkFreeformPackages(packageName)
        val isCallSelf = callingPackage != null && packageName == callingPackage
        val result = if (isInFreeformPackages) {
            isCallSelf
        } else {
            val isMatch = !isCallSelf
                    && packageName !in TARGET_PACKAGE_BLACKLIST
                    && intent.component !in TARGET_COMPONENT_BLACKLIST
                    && (intent.action in ACTIONS || intent.component in TARGET_COMPONENT_WHITELIST) // || checkUri(intent))
            if (isMatch) {
                addFreeformPackage(packageName)
            }
            isMatch
        }
        loggerD(msg = "check intent=$intent,caller=$callingPackage,result=$result,isCallSelf=$isCallSelf,isInFreeformPackages=$isInFreeformPackages")
        return result
    }

    // TODO: support multiuser
    private fun checkCaller(packageName: String?): Boolean {
        if (packageName == null) return false
        val callingUid = Binder.getCallingUid()
        if (mContext.packageManager.getPackageUid(packageName, 0) != callingUid) {
            loggerW(msg = "caller package $packageName does not match $callingUid")
            return false
        }
        return true
    }

    private fun checkQQShareSDK(intent: Intent): Boolean {
        // 会导致 SDK 反馈为分享取消
        if (intent.action == Intent.ACTION_VIEW && intent.scheme == "mqqapi" && intent.data?.authority == "share") {
            addFreeformPackage(QQ_PACKAGE_NAME)
            loggerD(msg = "match QQ Share SDK")
            return true
        }
        return false
    }

    private fun checkUri(intent: Intent): Boolean {
        if (intent.action == Intent.ACTION_VIEW) {
            val scheme = intent.data?.scheme
            // Open browser
            if (scheme == "http" || scheme == "https") return true
        }
        return false
    }

    private fun HookParam.beforeHookCommon(
        intentIndex: Int = 3,
        optionsIndex: Int = 10,
        shouldCheckCaller: Boolean = false,
        callingPackageIndex: Int = 1
    ) {
        if (!mEnabled) return
        val callingPackage = args[callingPackageIndex] as? String?
        if (shouldCheckCaller && !checkCaller(callingPackage)) return
        val intent = args[intentIndex] as? Intent ?: return
        if (!shouldInject(intent, callingPackage)) return
        intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK
        args(optionsIndex).set(injectFreeFormOptions(args[optionsIndex] as? Bundle?))
    }

    private fun updateState(key: String, newValue: Boolean? = null) {
        mEnabled = newValue ?: prefs.getBoolean(key)
        loggerI(msg = "$key=$mEnabled")
    }

    override fun onHook() {
        val key = moduleAppResources.getString(R.string.start_activity_hook_key)
        dataChannel.wait<Boolean>(key) {
            updateState(key, it)
        }
        updateState(key)
        val classIApplicationThread = findClass("android.app.IApplicationThread").normalClass!!
        val classProfilerInfo = findClass("android.app.ProfilerInfo").normalClass!!
        "com.android.server.wm.ActivityTaskManagerService".hook {
            injectMember {
                method {
                    name = "startActivityAsUser"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        param(
                            classIApplicationThread, // caller
                            String::class.java, // callingPackage
                            String::class.java, // callingFeatureId
                            IntentClass, // intent
                            String::class.java, // resolvedType
                            IBinderClass, // resultTo
                            String::class.java, // resultWho
                            Integer.TYPE, // requestCode
                            Integer.TYPE, // startFlags
                            classProfilerInfo, // ProfilerInfo
                            BundleClass, // bOptions
                            Integer.TYPE, // userId
                            java.lang.Boolean.TYPE // validateIncomingUser
                        )
                    } else {
                        param(
                            classIApplicationThread, // caller
                            String::class.java, // callingPackage
                            IntentClass, // intent
                            String::class.java, // resolvedType
                            IBinderClass, // resultTo
                            String::class.java, // resultWho
                            Integer.TYPE, // requestCode
                            Integer.TYPE, // startFlags
                            classProfilerInfo, // ProfilerInfo
                            BundleClass, // bOptions
                            Integer.TYPE, // userId
                            java.lang.Boolean.TYPE // validateIncomingUser
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
                        String::class.java, // callingPackage
                        IntentClass, // intent
                        String::class.java, // resolvedType
                        IBinderClass, // resultTo
                        String::class.java, // resultWho
                        Integer.TYPE, // requestCode
                        Integer.TYPE, // flags
                        classProfilerInfo, // ProfilerInfo
                        BundleClass, // options
                        IBinderClass, // permissionToken
                        java.lang.Boolean.TYPE, // ignoreTargetSecurity
                        Integer.TYPE // userId
                    )
                }
                beforeHook {
                    this.beforeHookCommon(intentIndex = 2, optionsIndex = 9, shouldCheckCaller = false)
                }
            }
            injectMember {
                method {
                    name = "startActivityAndWait"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        param(
                            classIApplicationThread, // caller
                            String::class.java, // callingPackage
                            String::class.java, // callingFeatureId
                            IntentClass, // intent
                            String::class.java, // resolvedType
                            IBinderClass, // resultTo
                            String::class.java, // resultWho
                            Integer.TYPE, // requestCode
                            Integer.TYPE, // flags
                            classProfilerInfo, // ProfilerInfo
                            BundleClass, // options
                            Integer.TYPE // userId
                        )
                    } else {
                        param(
                            classIApplicationThread, // caller
                            String::class.java, // callingPackage
                            IntentClass, // intent
                            String::class.java, // resolvedType
                            IBinderClass, // resultTo
                            String::class.java, // resultWho
                            Integer.TYPE, // requestCode
                            Integer.TYPE, // flags
                            classProfilerInfo, // ProfilerInfo
                            BundleClass, // options
                            Integer.TYPE // userId
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
                            String::class.java, // callingPackage
                            String::class.java, // callingFeatureId
                            IntentClass, // intent
                            String::class.java, // resolvedType
                            IBinderClass, // resultTo
                            String::class.java, // resultWho
                            Integer.TYPE, // requestCode
                            Integer.TYPE, // startFlags
                            ConfigurationClass, // newConfig
                            BundleClass, // options
                            Integer.TYPE // userId
                        )
                    } else {
                        param(
                            classIApplicationThread, // caller
                            String::class.java, // callingPackage
                            IntentClass, // intent
                            String::class.java, // resolvedType
                            IBinderClass, // resultTo
                            String::class.java, // resultWho
                            Integer.TYPE, // requestCode
                            Integer.TYPE, // startFlags
                            ConfigurationClass, // newConfig
                            BundleClass, // options
                            Integer.TYPE // userId
                        )
                    }
                }
                beforeHook {
                    this.beforeHookCommon()
                }
            }
        }
    }
}