package five.ec1cff.scene_freeform.viewmodels

import android.content.Context
import android.os.Handler
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.log.loggerE
import five.ec1cff.scene_freeform.BuildConfig
import five.ec1cff.scene_freeform.config.ConfigProvider
import five.ec1cff.scene_freeform.config.Constants
import kotlin.concurrent.thread

data class RemoteStatus(
    val status: Status = Status.CHECKING,
    val version: String? = null
) {
    enum class Status {
        INJECTED,
        CHECKING,
        VERSION_NOT_MATCH,
        UNKNOWN_ERROR
    }
}

class ModuleStatusViewModel: ViewModel() {
    val systemServerStatus = MutableLiveData<RemoteStatus>()
    val systemUIStatus = MutableLiveData<RemoteStatus>()

    fun checkRemoteStatus() {
        systemServerStatus.value = RemoteStatus()
        systemUIStatus.value = RemoteStatus()
        thread {
            kotlin.runCatching {
                val sysVer = ConfigProvider.system.version
                systemServerStatus.postValue(
                    RemoteStatus(
                        status = if (sysVer == BuildConfig.VERSION_NAME)
                            RemoteStatus.Status.INJECTED
                        else
                            RemoteStatus.Status.VERSION_NOT_MATCH,
                        version = sysVer
                    )
                )
            }.onFailure {
                loggerE(msg = "check system status", e = it)
                systemServerStatus.postValue(RemoteStatus(RemoteStatus.Status.UNKNOWN_ERROR))
            }
        }
        thread {
            kotlin.runCatching {
                val sysVer = ConfigProvider.systemUi.version
                systemUIStatus.postValue(
                    RemoteStatus(
                        status = if (sysVer == BuildConfig.VERSION_NAME)
                            RemoteStatus.Status.INJECTED
                        else
                            RemoteStatus.Status.VERSION_NOT_MATCH,
                        version = sysVer
                    )
                )
            }.onFailure {
                loggerE(msg = "check systemui status", e = it)
                systemUIStatus.postValue(RemoteStatus(RemoteStatus.Status.UNKNOWN_ERROR))
            }
        }
    }
}