package five.ec1cff.scene_freeform.hook

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.normalClass
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.android.ConfigurationClass
import com.highcapable.yukihookapi.hook.type.android.IBinderClass
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import five.ec1cff.scene_freeform.R
import five.ec1cff.scene_freeform.injectFreeFormOptions

object SystemServerHooker: YukiBaseHooker() {
    private var mEnabled = false
    private val ACTIONS = listOf(
        Intent.ACTION_SEND,
        Intent.ACTION_SEND_MULTIPLE,
        // Intent.ACTION_VIEW,
        Intent.ACTION_GET_CONTENT,
        Intent.ACTION_OPEN_DOCUMENT
    )

    private val TARGET_COMPONENT_BLACKLIST = listOf(
        ComponentName("android", "com.android.internal.app.ChooserActivity"),
        ComponentName("android", "com.android.internal.app.ResolverActivity"),
    )

    private val TARGET_PACKAGE_BLACKLIST = listOf(
        "android",
        "com.android.camera"
    )

    private fun checkIntent(intent: Intent): Boolean {
        return intent.action !in ACTIONS
                || intent.component == null
                || (intent.`package` ?: intent.component?.packageName) in TARGET_PACKAGE_BLACKLIST
                || intent.component in TARGET_COMPONENT_BLACKLIST
    }

    private fun HookParam.beforeHookCommon(intentIndex: Int = 3, optionsIndex: Int = 10) {
        if (!mEnabled) return
        val intent = args[intentIndex] as? Intent ?: return
        if (checkIntent(intent)) return
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
        val IApplicationThreadClass = findClass("android.app.IApplicationThread").normalClass!!
        val ProfilerInfoClass = findClass("android.app.ProfilerInfo").normalClass!!
        "com.android.server.wm.ActivityTaskManagerService".hook {
            injectMember {
                method {
                    name = "startActivity"
                    param(
                        IApplicationThreadClass, // caller
                        String::class.java, // callingPackage
                        String::class.java, // callingFeatureId
                        IntentClass, // intent
                        String::class.java, // resolvedType
                        IBinderClass, // resultTo
                        String::class.java, // resultWho
                        Integer.TYPE, // requestCode
                        Integer.TYPE, // flags
                        ProfilerInfoClass, // ProfilerInfo
                        BundleClass // options
                    )
                }
                beforeHook {
                    this.beforeHookCommon()
                }
            }
            injectMember {
                method {
                    name = "startActivityAsUser"
                    param(
                        IApplicationThreadClass, // caller
                        String::class.java, // callingPackage
                        String::class.java, // callingFeatureId
                        IntentClass, // intent
                        String::class.java, // resolvedType
                        IBinderClass, // resultTo
                        String::class.java, // resultWho
                        Integer.TYPE, // requestCode
                        Integer.TYPE, // flags
                        ProfilerInfoClass, // ProfilerInfo
                        BundleClass, // options
                        Integer.TYPE // userId
                    )
                }
                beforeHook {
                    this.beforeHookCommon()
                }
            }
            injectMember {
                method {
                    name = "startActivityAsCaller"
                    param(
                        IApplicationThreadClass, // caller
                        String::class.java, // callingPackage
                        IntentClass, // intent
                        String::class.java, // resolvedType
                        IBinderClass, // resultTo
                        String::class.java, // resultWho
                        Integer.TYPE, // requestCode
                        Integer.TYPE, // flags
                        ProfilerInfoClass, // ProfilerInfo
                        BundleClass, // options
                        IBinderClass, // permissionToken
                        java.lang.Boolean.TYPE, // ignoreTargetSecurity
                        Integer.TYPE // userId
                    )
                }
                beforeHook {
                    this.beforeHookCommon(intentIndex = 2, optionsIndex = 9)
                }
            }
            injectMember {
                method {
                    name = "startActivityAndWait"
                    param(
                        IApplicationThreadClass, // caller
                        String::class.java, // callingPackage
                        String::class.java, // callingFeatureId
                        IntentClass, // intent
                        String::class.java, // resolvedType
                        IBinderClass, // resultTo
                        String::class.java, // resultWho
                        Integer.TYPE, // requestCode
                        Integer.TYPE, // flags
                        ProfilerInfoClass, // ProfilerInfo
                        BundleClass, // options
                        Integer.TYPE // userId
                    )
                }
                beforeHook {
                    this.beforeHookCommon()
                }
            }
            injectMember {
                method {
                    name = "startActivityWithConfig"
                    param(
                        IApplicationThreadClass, // caller
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
                }
                beforeHook {
                    this.beforeHookCommon()
                }
            }
        }
    }
}