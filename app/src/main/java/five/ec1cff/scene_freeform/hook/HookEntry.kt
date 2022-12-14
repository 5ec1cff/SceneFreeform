package five.ec1cff.scene_freeform.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.YukiGenerateApi
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.bridge.YukiHookBridge
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import five.ec1cff.scene_freeform.BuildConfig
import five.ec1cff.scene_freeform.bridge.CPBridge
import five.ec1cff.scene_freeform.config.Constants
import five.ec1cff.scene_freeform.hook.system.SystemServerHooker

@InjectYukiHookWithXposed(isUsingResourcesHook = false, modulePackageName = BuildConfig.APPLICATION_ID)
class HookEntry: IYukiHookXposedInit {

    override fun onInit() {
        YukiHookAPI.Configs.apply {
            debugTag = "SceneFreeform"
            isDebug = BuildConfig.DEBUG
            isEnableModulePrefsCache = false
        }
    }

    @OptIn(YukiGenerateApi::class)
    override fun onHook() = YukiHookAPI.encase {
        loadApp(Constants.SYSTEM_UI_PACKAGE) {
            YukiHookAPI.Configs.debugTag = "SceneFreeform:systemui"
            loadHooker(SystemUIHooker)
        }
        loadSystem {
            YukiHookAPI.Configs.debugTag = "SceneFreeform:system"
            loadHooker(SystemServerHooker)
        }
        loadApp(BuildConfig.APPLICATION_ID) {
            // temporary fix for YukiHook
            YukiHookBridge.hookModuleAppStatus(appClassLoader, false)
        }
        loadHooker(CPBridge.CPBridgeHooker)
    }
}