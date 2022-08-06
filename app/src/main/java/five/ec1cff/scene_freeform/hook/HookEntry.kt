package five.ec1cff.scene_freeform.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed(isUsingResourcesHook = false)
class HookEntry: IYukiHookXposedInit {

    override fun onInit() {
        YukiHookAPI.Configs.apply {
            debugTag = "SceneFreeform"
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
    }
}