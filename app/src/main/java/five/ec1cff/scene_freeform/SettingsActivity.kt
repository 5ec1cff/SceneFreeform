package five.ec1cff.scene_freeform

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.factory.modulePrefs

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            val v = newValue as Boolean
            val activity = requireActivity()
            val notificationHookKey = getString(R.string.notification_hook_key)
            val startActivityHookKey = getString(R.string.start_activity_hook_key)
            activity.modulePrefs.putBoolean(preference.key, v)
            when (preference.key) {
                notificationHookKey -> {
                    activity.dataChannel("com.android.systemui")
                        .put(notificationHookKey, v)
                }
                startActivityHookKey -> {
                    activity.dataChannel("android")
                        .put(startActivityHookKey, v)
                }
            }
            return true
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val activity = requireActivity()
            var key = getString(R.string.notification_hook_key)
            findPreference<SwitchPreferenceCompat>(key)!!.also {
                it.onPreferenceChangeListener = this
                it.isChecked = activity.modulePrefs.getBoolean(key)
            }
            key = getString(R.string.start_activity_hook_key)
            findPreference<SwitchPreferenceCompat>(key)!!.also {
                it.onPreferenceChangeListener = this
                it.isChecked = activity.modulePrefs.getBoolean(key)
            }
        }
    }
}