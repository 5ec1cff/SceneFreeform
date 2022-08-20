package five.ec1cff.scene_freeform.fragments

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.log.loggerD
import five.ec1cff.scene_freeform.R
import five.ec1cff.scene_freeform.checkPackageExists
import five.ec1cff.scene_freeform.config.Constants
import five.ec1cff.scene_freeform.config.MyPreferenceDataStore
import five.ec1cff.scene_freeform.viewmodels.ModuleStatusViewModel
import five.ec1cff.scene_freeform.viewmodels.RemoteStatus

@Suppress("Unchecked_Cast")
class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private val viewModel by activityViewModels<ModuleStatusViewModel>()
    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        (preference as? SwitchPreferenceCompat)?.also {
            val nv = newValue as Boolean
            it.isChecked = nv
            MyPreferenceDataStore.putBoolean(it.key, nv)
            pushConfig(it.key, nv)
        }
        return true
    }

    private val resultListener = { key: String, arg: Bundle ->
        val list = arg.getSerializable(AppSelectorFragment.KEY_SELECTED_APPS) as? HashSet<String>
        if (list != null) {
            pushConfig(key, list)
            MyPreferenceDataStore.putStringSet(key, list)
            findPreference<Preference>(key)?.also {
                val count = list.size
                it.summary = if (count > 0) getString(R.string.scope_summary, count)
                else getString(R.string.scope_summary_empty)
            }
        }
    }

    private fun pushConfig(key: String, value: Any) {
        val bundle = bundleOf(key to value)
        val activity = requireActivity()
        if (key in Constants.systemServerConfigs) {
            activity.dataChannel(Constants.SYSTEM_SERVER_PACKAGE)
                .put(Constants.CHANNEL_DATA_UPDATE_CONFIG, bundle)
        }
        if (key in Constants.systemUIConfigs) {
            activity.dataChannel(Constants.SYSTEM_UI_PACKAGE)
                .put(Constants.CHANNEL_DATA_UPDATE_CONFIG, bundle)
        }
    }

    private fun updatePreferenceState() {
        val moduleAvailable = YukiHookAPI.Status.isModuleActive
        val systemServerAvailable = viewModel.systemServerStatus.value?.status == RemoteStatus.Status.INJECTED
        val systemUiAvailable = viewModel.systemUIStatus.value?.status == RemoteStatus.Status.INJECTED
        findPreference<PreferenceCategory>("notification_category")!!.isEnabled = moduleAvailable && systemUiAvailable
        findPreference<PreferenceCategory>("app_jump_category")!!.isEnabled = moduleAvailable && systemServerAvailable
        findPreference<PreferenceCategory>("screen_category")!!.isEnabled = moduleAvailable && (systemUiAvailable || systemServerAvailable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener(Constants.NOTIFICATION_SCOPE, resultListener)
        setFragmentResultListener(Constants.APP_JUMP_SCOPE, resultListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.systemServerStatus.observe(viewLifecycleOwner) {
            updatePreferenceState()
        }
        viewModel.systemUIStatus.observe(viewLifecycleOwner) {
            updatePreferenceState()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        val activity = requireActivity()
        findPreference<SwitchPreferenceCompat>("handle_qq_share")!!.also {
            if (activity.checkPackageExists("com.tencent.mobileqq")) {
                it.isVisible = true
            }
        }
        findPreference<SwitchPreferenceCompat>("handle_wechat_share")!!.also {
            if (activity.checkPackageExists("com.tencent.mm")) {
                it.isVisible = true
            }
        }
        findPreference<Preference>(Constants.NOTIFICATION_SCOPE)!!.also {
            val count = MyPreferenceDataStore.getStringSet(Constants.NOTIFICATION_SCOPE).size
            it.summary = if (count > 0) getString(R.string.scope_summary, count)
            else getString(R.string.scope_summary_empty)
            it.setOnPreferenceClickListener {
                val navController = NavHostFragment.findNavController(this)
                navController.navigate(R.id.settings_fragment_to_app_selector_fragment,
                    bundleOf(
                        AppSelectorFragment.KEY_RESULT_KEY to Constants.NOTIFICATION_SCOPE,
                        AppSelectorFragment.KEY_SELECTED_APPS to MyPreferenceDataStore.getStringSet(Constants.NOTIFICATION_SCOPE)
                    )
                )
                true
            }
        }
        findPreference<Preference>(Constants.APP_JUMP_SCOPE)!!.also {
            val count = MyPreferenceDataStore.getStringSet(Constants.APP_JUMP_SCOPE).size
            it.summary = if (count > 0) getString(R.string.scope_summary, count)
            else getString(R.string.scope_summary_empty)
            it.setOnPreferenceClickListener {
                val navController = NavHostFragment.findNavController(this)
                navController.navigate(R.id.settings_fragment_to_app_selector_fragment,
                    bundleOf(
                        AppSelectorFragment.KEY_RESULT_KEY to Constants.APP_JUMP_SCOPE,
                        AppSelectorFragment.KEY_SELECTED_APPS to MyPreferenceDataStore.getStringSet(Constants.APP_JUMP_SCOPE)
                    )
                )
                true
            }
        }
        for (k in Constants.systemServerConfigs) {
            val p = findPreference<Preference>(k)
            ((p) as? SwitchPreferenceCompat)?.apply {
                this.isChecked = MyPreferenceDataStore.getBoolean(key, false)
                onPreferenceChangeListener = this@SettingsFragment
            }
            loggerD(msg = "$k: ${p?.preferenceDataStore}")
        }
        for (k in Constants.systemUIConfigs) {
            val p = findPreference<Preference>(k)
            ((p) as? SwitchPreferenceCompat)?.apply {
                this.isChecked = MyPreferenceDataStore.getBoolean(key, false)
                onPreferenceChangeListener = this@SettingsFragment
            }
            loggerD(msg = "$k: ${p?.preferenceDataStore}")
        }
    }
}