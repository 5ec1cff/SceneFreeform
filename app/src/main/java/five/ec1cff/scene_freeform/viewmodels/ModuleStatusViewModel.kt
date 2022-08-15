package five.ec1cff.scene_freeform.viewmodels

import android.content.Context
import android.os.Handler
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.highcapable.yukihookapi.hook.factory.dataChannel
import five.ec1cff.scene_freeform.BuildConfig
import five.ec1cff.scene_freeform.config.Constants

data class RemoteStatus(
    val status: Status = Status.CHECKING,
    val version: String? = null
) {
    enum class Status {
        INJECTED,
        CHECKING,
        VERSION_NOT_MATCH,
        TIMEOUT
    }
}

class ModuleStatusViewModel: ViewModel() {
    val systemServerStatus = MutableLiveData<RemoteStatus>()
    val systemUIStatus = MutableLiveData<RemoteStatus>()
    private var needCheck = false

    fun checkRemoteStatus(context: Context, handler: Handler) {
        if (needCheck) return
        systemServerStatus.value = RemoteStatus()
        systemUIStatus.value = RemoteStatus()
        context.dataChannel(Constants.SYSTEM_SERVER_PACKAGE).with {
            put(Constants.CHANNEL_DATA_GET_VERSION_SS, BuildConfig.VERSION_NAME)
            wait(Constants.CHANNEL_DATA_GET_VERSION_SS) {
                if (it == BuildConfig.VERSION_NAME)
                    systemServerStatus.value = RemoteStatus(RemoteStatus.Status.INJECTED, it)
                else
                    systemServerStatus.value =
                        RemoteStatus(RemoteStatus.Status.VERSION_NOT_MATCH, it)
            }
        }
        context.dataChannel(Constants.SYSTEM_UI_PACKAGE).with {
            put(Constants.CHANNEL_DATA_GET_VERSION_SU, BuildConfig.VERSION_NAME)
            wait(Constants.CHANNEL_DATA_GET_VERSION_SU) {
                if (it == BuildConfig.VERSION_NAME)
                    systemUIStatus.value = RemoteStatus(RemoteStatus.Status.INJECTED, it)
                else
                    systemUIStatus.value = RemoteStatus(RemoteStatus.Status.VERSION_NOT_MATCH, it)
            }
        }
        handler.postDelayed(
            {
                if (systemServerStatus.value?.status == RemoteStatus.Status.CHECKING) {
                    systemServerStatus.value = RemoteStatus(RemoteStatus.Status.TIMEOUT, null)
                }
                if (systemUIStatus.value?.status == RemoteStatus.Status.CHECKING) {
                    systemUIStatus.value = RemoteStatus(RemoteStatus.Status.TIMEOUT, null)
                }
            },
            3000L
        )
        needCheck = true
    }
}