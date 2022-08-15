package five.ec1cff.scene_freeform.viewmodels

import android.app.Application
import android.content.pm.PackageInfo
import android.widget.Filter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.util.*
import java.util.concurrent.Executors

data class AppItem(
    val packageInfo: PackageInfo,
    val packageName: String,
    val label: String
)

class AppSelectorViewModel(application: Application) : AndroidViewModel(application) {
    val filteredAppList = MutableLiveData<List<AppItem>>()
    private val executorService by lazy { Executors.newCachedThreadPool() }
    val checkedSet = hashSetOf<String>()
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
                    val item1IsChecked = item1.packageName in checkedSet
                    val item2IsChecked = item2.packageName in checkedSet
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

    fun refresh() {
        val pm = getApplication<Application>().packageManager
        executorService.submit {
            appList = pm.getInstalledPackages(0).map {
                AppItem(it, it.packageName, it.applicationInfo.loadLabel(pm).toString())
            }
            mFilter.filter(mQuery)
        }
    }

    fun query(q: CharSequence?) {
        mQuery = q
        mFilter.filter(mQuery)
    }
}