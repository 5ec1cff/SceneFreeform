package five.ec1cff.scene_freeform.config

import androidx.preference.PreferenceDataStore
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

object MyPreferenceDataStore: PreferenceDataStore() {
    private val prefs by lazy {
        ModuleApplication.appContext.modulePrefs
    }
    override fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return prefs.getBoolean(key)
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String> {
        return prefs.getStringSet(key, defValues ?: hashSetOf()) as MutableSet<String>
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        values?.let { prefs.putStringSet(key, it) }
    }

    fun getStringSet(key: String): MutableSet<String> {
        return getStringSet(key, null)
    }
}