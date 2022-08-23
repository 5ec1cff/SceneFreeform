package five.ec1cff.scene_freeform.config

import android.os.Bundle
import com.highcapable.yukihookapi.hook.xposed.channel.data.ChannelData
import five.ec1cff.scene_freeform.BuildConfig

object Constants {
    const val SYSTEM_PACKAGE = "android"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    const val QQ_PACKAGE_NAME = "com.tencent.mobileqq"
    const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
    const val FOO_VIEW_PACKAGE_NAME = "com.fooview.android.fooview"

    const val NOTIFICATION = "notification"
    const val NOTIFICATION_SCOPE = "notification_scope"
    const val APP_JUMP = "app_jump"
    const val APP_JUMP_SHARE = "app_jump_share"
    const val APP_JUMP_BROWSER = "app_jump_browser"
    const val APP_JUMP_FILE_SELECTOR = "app_jump_file_selector"
    const val APP_JUMP_SETTINGS = "app_jump_settings"
    const val APP_JUMP_SCOPE = "app_jump_scope"
    const val HANDLE_QQ_SHARE = "handle_qq_share"
    const val HANDLE_WECHAT_SHARE = "handle_wechat_share"
    const val LANDSCAPE = "landscape"

    val systemServerConfigs = listOf(
        APP_JUMP,
        APP_JUMP_SHARE,
        APP_JUMP_BROWSER,
        APP_JUMP_FILE_SELECTOR,
        APP_JUMP_SETTINGS,
        APP_JUMP_SCOPE,
        HANDLE_QQ_SHARE,
        HANDLE_WECHAT_SHARE,
        LANDSCAPE
    )

    val systemUIConfigs = listOf(
        NOTIFICATION,
        NOTIFICATION_SCOPE,
        LANDSCAPE
    )
}
