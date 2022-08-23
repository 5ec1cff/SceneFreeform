package five.ec1cff.scene_freeform.viewmodels

import android.app.Application
import android.content.pm.PackageInfo
import android.widget.Filter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.highcapable.yukihookapi.hook.log.loggerE
import five.ec1cff.scene_freeform.config.AppJumpRule
import five.ec1cff.scene_freeform.config.ConfigProvider
import five.ec1cff.scene_freeform.config.Constants
import five.ec1cff.scene_freeform.config.NotificationScope
import five.ec1cff.scene_freeform.getInstalledPackagesAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

data class AppItem(
    val packageInfo: PackageInfo,
    val packageName: String,
    val label: String,
    var isChecked: Boolean
)

private interface Adapter {
    suspend fun addPackage(name: String)
    suspend fun removePackage(name: String)
    suspend fun getCheckedPackages(): Collection<String>
}

class AppSelectorViewModel(application: Application) : AndroidViewModel(application) {
    val filteredAppList = MutableLiveData<List<AppItem>>()
    private lateinit var mAdapter: Adapter
    private var appList = listOf<AppItem>()
    private var mQuery: CharSequence? = null
    private val mFilter = object : Filter() {
        override fun performFiltering(query: CharSequence?): FilterResults =
            FilterResults().apply {
                values = appList.filter { item ->
                    query?.toString()?.lowercase()?.let { keyword ->
                        item.label.contains(keyword) || item.packageName.contains(keyword)
                    } ?: true
                }.sortedWith { item1, item2 ->
                    val item1IsChecked = item1.isChecked
                    val item2IsChecked = item2.isChecked
                    if (item1IsChecked != item2IsChecked)
                        return@sortedWith if (item1IsChecked) -1 else 1
                    return@sortedWith Collections.reverseOrder(Comparator.comparingLong<AppItem> { item -> item.packageInfo.lastUpdateTime }).compare(item1, item2)
                }
            }

        @Suppress("Unchecked_Cast")
        override fun publishResults(query: CharSequence?, results: FilterResults) {
            val newList = (results.values as? List<AppItem>) ?: appList
            filteredAppList.value = newList
        }
    }

    fun setType(name: String) {
        when (name) {
            Constants.NOTIFICATION_SCOPE -> {
                mAdapter = object : Adapter {
                    override suspend fun addPackage(name: String) {
                        ConfigProvider.dataBase.configsDao().setNotificationScopes(
                            NotificationScope(name, inWhitelist = true, inBlacklist = false)
                        )
                        kotlin.runCatching {
                            ConfigProvider.systemUi.setPackageWhitelist(name, true)
                        }.onFailure { loggerE(msg = "addNotificationScope", e = it) }
                    }

                    override suspend fun removePackage(name: String) {
                        ConfigProvider.dataBase.configsDao().setNotificationScopes(
                            NotificationScope(name, inWhitelist = false, inBlacklist = false)
                        )
                        kotlin.runCatching {
                            ConfigProvider.systemUi.setPackageWhitelist(name, false)
                        }.onFailure { loggerE(msg = "removeNotificationScope", e = it) }
                    }

                    override suspend fun getCheckedPackages(): Collection<String> =
                        ConfigProvider.dataBase.configsDao().getNotificationScopes()
                            .filter { it.inWhitelist }
                            .map { it.packageName }
                }
            }
            Constants.APP_JUMP_SCOPE -> {
                mAdapter = object : Adapter {
                    override suspend fun addPackage(name: String) {
                        ConfigProvider.dataBase.configsDao().setAppJumpRules(
                            AppJumpRule(name, allowTarget = false, allowSource = false)
                        )
                        kotlin.runCatching {
                            ConfigProvider.system.setPackageBlacklist(name, true)
                        }.onFailure { loggerE(msg = "addAppJumpScope", e = it) }
                    }

                    override suspend fun removePackage(name: String) {
                        ConfigProvider.dataBase.configsDao().setAppJumpRules(
                            AppJumpRule(name, allowTarget = true, allowSource = false)
                        )
                        kotlin.runCatching {
                            ConfigProvider.system.setPackageBlacklist(name, false)
                        }.onFailure { loggerE(msg = "removeAppJumpScope", e = it) }
                    }

                    override suspend fun getCheckedPackages(): Collection<String> =
                        ConfigProvider.dataBase.configsDao().getAppJumpRules()
                            .filter { it.allowTarget.not() }
                            .map { it.packageName }
                }
            }
        }
    }

    fun setPackageChecked(name: String, checked: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (checked) mAdapter.addPackage(name)
                else mAdapter.removePackage(name)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val checkedList = withContext(Dispatchers.IO) {
                mAdapter.getCheckedPackages()
            }
            appList = getApplication<Application>().getInstalledPackagesAsync(0).map {
                // TODO: load label with cache
                AppItem(it, it.packageName, it.packageName, it.packageName in checkedList)
            }
            mFilter.filter(mQuery)
        }
    }

    fun query(q: CharSequence?) {
        mQuery = q
        mFilter.filter(mQuery)
    }
}