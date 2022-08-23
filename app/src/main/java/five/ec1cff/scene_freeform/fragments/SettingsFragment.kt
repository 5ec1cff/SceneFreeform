package five.ec1cff.scene_freeform.fragments

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.highcapable.yukihookapi.YukiHookAPI
import five.ec1cff.scene_freeform.R
import five.ec1cff.scene_freeform.checkPackageExists
import five.ec1cff.scene_freeform.checkPackageExistsAsync
import five.ec1cff.scene_freeform.config.*
import five.ec1cff.scene_freeform.viewmodels.ModuleStatusViewModel
import five.ec1cff.scene_freeform.viewmodels.RemoteStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("Unchecked_Cast")
class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private val viewModel by activityViewModels<ModuleStatusViewModel>()
    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        (preference as? SwitchPreferenceCompat)?.also {
            val nv = newValue as Boolean
            it.isChecked = nv
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    ConfigProvider.dataBase.configsDao().setConfig(
                        Config(name = preference.key, type = Config.TYPE_BOOLEAN, value = nv.toString() )
                    )
                }
            }
            pushConfig(it.key, nv)
        }
        return true
    }

    private fun pushConfig(key: String, value: Any) {
        val bundle = bundleOf(key to value)
        if (key in Constants.systemServerConfigs) {
            ConfigProvider.system.updateConfig(bundle)
        }
        if (key in Constants.systemUIConfigs) {
            ConfigProvider.systemUi.updateConfig(bundle)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.systemServerStatus.observe(viewLifecycleOwner) {
            updatePreferenceState()
        }
        viewModel.systemUIStatus.observe(viewLifecycleOwner) {
            updatePreferenceState()
        }
        ConfigProvider.dataBase.configsDao().apply {
            getAllConfigsFlow()
                .distinctUntilChanged()
                .onEach { configs ->
                    configs.forEach { config ->
                        if (config.type == Config.TYPE_BOOLEAN) {
                            findPreference<SwitchPreferenceCompat>(config.name)?.also {
                                it.isChecked = config.value.toBoolean()
                            }
                        }
                    }
                }.launchIn(viewLifecycleOwner.lifecycleScope)
            getNotificationScopesCountFlow()
                .distinctUntilChanged()
                .onEach { count ->
                    findPreference<Preference>(Constants.NOTIFICATION_SCOPE)?.also {
                        it.summary = if (count > 0) getString(R.string.scope_summary, count)
                        else getString(R.string.scope_summary_empty)
                    }
                }.launchIn(viewLifecycleOwner.lifecycleScope)
            getAppJumpRulesCountFlow()
                .distinctUntilChanged()
                .onEach { count ->
                    findPreference<Preference>(Constants.APP_JUMP_SCOPE)?.also {
                        it.summary = if (count > 0) getString(R.string.scope_summary, count)
                        else getString(R.string.scope_summary_empty)
                    }
                }.launchIn(viewLifecycleOwner.lifecycleScope)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        val activity = requireActivity()
        lifecycleScope.launch {
            findPreference<SwitchPreferenceCompat>("handle_qq_share")?.isVisible =
                activity.checkPackageExistsAsync(Constants.QQ_PACKAGE_NAME)
            findPreference<SwitchPreferenceCompat>("handle_wechat_share")?.isVisible =
                activity.checkPackageExistsAsync(Constants.WECHAT_PACKAGE_NAME)
        }
        findPreference<Preference>(Constants.NOTIFICATION_SCOPE)!!.setOnPreferenceClickListener {
            val navController = NavHostFragment.findNavController(this@SettingsFragment)
            navController.navigate(
                R.id.settings_fragment_to_app_selector_fragment,
                bundleOf(
                    AppSelectorFragment.KEY_TYPE to Constants.NOTIFICATION_SCOPE
                )
            )
            true
        }
        findPreference<Preference>(Constants.APP_JUMP_SCOPE)!!.setOnPreferenceClickListener {
            val navController = NavHostFragment.findNavController(this@SettingsFragment)
            navController.navigate(
                R.id.settings_fragment_to_app_selector_fragment,
                bundleOf(AppSelectorFragment.KEY_TYPE to Constants.APP_JUMP_SCOPE)
                )
            true
        }
        for (k in Constants.systemServerConfigs) {
            val p = findPreference<Preference>(k)
            (p as? SwitchPreferenceCompat)?.onPreferenceChangeListener = this@SettingsFragment
        }
        for (k in Constants.systemUIConfigs) {
            val p = findPreference<Preference>(k)
            (p as? SwitchPreferenceCompat)?.onPreferenceChangeListener = this@SettingsFragment
        }
    }
}