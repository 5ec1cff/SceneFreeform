package five.ec1cff.scene_freeform

import android.content.Context
import android.content.pm.PackageInfo
import com.bumptech.glide.Glide
import five.ec1cff.scene_freeform.R
import android.graphics.Bitmap
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import me.zhanghai.android.appiconloader.glide.AppIconModelLoader

@GlideModule
class AppGlideModule : AppGlideModule() {
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun registerComponents(
        context: Context, glide: Glide,
        registry: Registry
    ) {
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.app_icon_size)
        registry.prepend(
            PackageInfo::class.java, Bitmap::class.java, AppIconModelLoader.Factory(
                iconSize,
                false, context
            )
        )
    }
}