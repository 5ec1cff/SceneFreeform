package five.ec1cff.scene_freeform.hook

import android.content.Context
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.type.android.*
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.ListClass
import com.highcapable.yukihookapi.hook.type.java.StringType
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import five.ec1cff.scene_freeform.BuildConfig
import java.lang.Exception

@InjectYukiHookWithXposed(isUsingResourcesHook = false)
class HookEntry: IYukiHookXposedInit {

    override fun onInit() {
        YukiHookAPI.Configs.apply {
            debugTag = "SceneFreeform"
            isDebug = BuildConfig.DEBUG
            isEnableModulePrefsCache = false
        }
    }

    override fun onHook() = YukiHookAPI.encase {
        loadApp("com.android.systemui") {
            YukiHookAPI.Configs.debugTag = "SceneFreeform:systemui"
            loadHooker(SystemUIHooker)
        }
        loadSystem {
            YukiHookAPI.Configs.debugTag = "SceneFreeform:system"
            loadHooker(SystemServerHooker)
        }
        loadApp(BuildConfig.APPLICATION_ID) {
            ContextImplClass.hook {
                injectMember {
                    method {
                        name = "setFilePermissionsFromMode"
                    }
                    beforeHook {
                        if ((args[0] as String).endsWith("preferences.xml")) {
                            args(1).set(Context.MODE_WORLD_READABLE)
                        }
                    }
                }
            }
        }
    }
}