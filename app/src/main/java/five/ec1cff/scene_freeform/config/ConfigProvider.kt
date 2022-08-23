package five.ec1cff.scene_freeform.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.room.Room
import com.highcapable.yukihookapi.hook.log.loggerE
import five.ec1cff.scene_freeform.BuildConfig
import five.ec1cff.scene_freeform.bridge.CPBridge
import five.ec1cff.scene_freeform.bridge.IModuleApp
import five.ec1cff.scene_freeform.bridge.ISystemServer
import five.ec1cff.scene_freeform.bridge.ISystemUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class ConfigProvider : ContentProvider() {

    companion object {
        private lateinit var instance: ConfigProvider
        val dataBase by lazy {
            Room.databaseBuilder(
                instance.requireContext().createDeviceProtectedStorageContext(),
                ConfigDB::class.java,
                "config"
            ).build()
        }
        val system by lazy {
            ISystemServer.Stub.asInterface(
                CPBridge.getBinderForSystem(instance.requireContext())
            )
        }
        val systemUi by lazy {
            ISystemUI.Stub.asInterface(
                CPBridge.getBinderForSystemUI(instance.requireContext()).also {
                    it?.linkToDeath({
                        loggerE(msg = "SystemUI died, kill self!")
                        exitProcess(-1)
                    }, 0)
                }
            )
        }
    }

    internal object BinderHandler : IModuleApp.Stub() {
        override fun requireConfig(version: String, from: String): Bundle {
            if (version != BuildConfig.VERSION_NAME)
                throw IllegalArgumentException("version does not match (local ${BuildConfig.VERSION_NAME}, remote $version)")
            val bundle = Bundle()
            dataBase.configsDao().apply {
                if (from == Constants.SYSTEM_PACKAGE) {
                    getAllConfigs().forEach {
                        if (it.name in Constants.systemServerConfigs && it.type == Config.TYPE_BOOLEAN) {
                            bundle.putBoolean(it.name, it.value.toBoolean())
                        }
                    }
                    runBlocking(Dispatchers.IO) {
                        bundle.putStringArrayList(Constants.APP_JUMP_SCOPE, ArrayList<String?>().apply {
                            addAll(getAppJumpRules()
                                .filter { it.allowTarget.not() }
                                .map { it.packageName })
                        })
                    }
                } else if (from == Constants.SYSTEM_UI_PACKAGE) {
                    getAllConfigs().forEach {
                        if (it.name in Constants.systemUIConfigs && it.type == Config.TYPE_BOOLEAN) {
                            bundle.putBoolean(it.name, it.value.toBoolean())
                        }
                    }
                    runBlocking(Dispatchers.IO) {
                        bundle.putStringArrayList(Constants.NOTIFICATION_SCOPE, ArrayList<String?>().apply {
                            addAll(getNotificationScopes()
                                .filter { it.inWhitelist }
                                .map { it.packageName })
                        })
                    }
                }
            }
            return bundle
        }
    }

    override fun onCreate(): Boolean {
        instance = this
        CPBridge.setBinderForBridge(BinderHandler.asBinder())
        return true
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? = null

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}