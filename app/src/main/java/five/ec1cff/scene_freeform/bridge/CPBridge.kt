package five.ec1cff.scene_freeform.bridge

import android.app.PendingIntent
import android.content.*
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import androidx.annotation.Keep
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.type.android.IBinderClass
import com.highcapable.yukihookapi.hook.type.android.ParcelClass
import com.highcapable.yukihookapi.hook.type.java.IntType
import five.ec1cff.scene_freeform.BuildConfig
import five.ec1cff.scene_freeform.config.ConfigProvider
import five.ec1cff.scene_freeform.config.Constants

@Suppress("DiscouragedPrivateApi")
object CPBridge {
    private const val BRIDGE_TRANSACT_CODE = 1598246978 /* '_CPB' */
    private const val BRIDGE_INTERFACE_TOKEN = "${BuildConfig.APPLICATION_ID}.cp_bridge"
    private const val SET_BINDER_METHOD_NAME = "setBinderForBridge"
    private val mWhitelistPackages = mutableSetOf<String>(
        BuildConfig.APPLICATION_ID,
        Constants.SYSTEM_PACKAGE,
        Constants.SYSTEM_UI_PACKAGE
    )
    private var mBinder: IBinder? = null

    fun getBinderForSystem(context: Context) =
        getBinderFromBridge(context, "com.android.textclassifier.icons")

    fun getBinderForSystemUI(context: Context) =
        getBinderFromBridge(context, "com.android.systemui.keyguard")

    object CPBridgeHooker : YukiBaseHooker() {
        override fun onHook() {
            "android.content.ContentProviderNative".hook {
                injectMember {
                    method {
                        name = "onTransact"
                        param(IntType, ParcelClass, ParcelClass, IntType)
                    }
                    beforeHook {
                        if (args[0] != BRIDGE_TRANSACT_CODE) return@beforeHook
                        val data = args[1] as? Parcel ?: return@beforeHook
                        kotlin.runCatching {
                            data.enforceInterface(BRIDGE_INTERFACE_TOKEN)
                            val token = data.readParcelable<PendingIntent>(ClassLoader.getSystemClassLoader())
                            checkCallerPermission(token)
                            (args[2] as Parcel).writeStrongBinder(mBinder)
                            resultTrue()
                        }.onFailure {
                            loggerE(msg = "Error occurred while hook ContentProviderNative", e = it)
                            data.setDataPosition(0)
                        }
                    }
                }
            }
            // hook for module itself to set binder
            loadApp(BuildConfig.APPLICATION_ID) {
                CPBridge::class.java.hook {
                    injectMember {
                        method {
                            name = SET_BINDER_METHOD_NAME
                            param(IBinderClass)
                        }
                        replaceUnit {
                            setBinderForBridge(args[0] as? IBinder?)
                        }
                    }
                }
            }
        }
    }

    private val methodAcquireProvider by lazy {
        ContentResolver::class.java.getDeclaredMethod(
            "acquireUnstableProvider",
            Context::class.java,
            String::class.java
        ).also { it.isAccessible = true }
    }

    private val methodReleaseProvider by lazy {
        ContentResolver::class.java.getDeclaredMethod(
            "releaseUnstableProvider",
            IContentProvider::class.java
        ).also { it.isAccessible = true }
    }

    internal fun checkCallerPermission(token: PendingIntent?) {
        if (token == null)
            throw SecurityException("Token is null!")
        val uid = token.creatorUid
        if (uid != Binder.getCallingUid())
            throw SecurityException("Binder calling uid does not match token's uid!")
        val packageName = token.creatorPackage
        if (packageName !in mWhitelistPackages)
            throw SecurityException("Package $packageName is not in whitelist!")
    }

    private fun getToken(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent().apply {
            component = ComponentName("_", "_")
        }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)

    @Keep
    @JvmName(SET_BINDER_METHOD_NAME)
    fun setBinderForBridge(binder: IBinder?) {
        mBinder = binder
    }

    fun getBinderFromBridge(context: Context, authority: String): IBinder? {
        val data = Parcel.obtain()
        val result = Parcel.obtain()
        val resolver = context.contentResolver
        var provider: IContentProvider? = null
        try {
            provider = methodAcquireProvider.invoke(resolver, context, authority) as IContentProvider
            data.writeInterfaceToken(BRIDGE_INTERFACE_TOKEN)
            data.writeParcelable(getToken(context), 0)
            provider
                .asBinder()
                .transact(BRIDGE_TRANSACT_CODE, data, result, 0)
            return result.readStrongBinder()
        } finally {
            data.recycle()
            result.recycle()
            if (provider != null) {
                methodReleaseProvider.invoke(resolver, provider)
            }
        }
    }
}