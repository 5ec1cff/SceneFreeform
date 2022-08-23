package five.ec1cff.scene_freeform.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.color.MaterialColors
import com.highcapable.yukihookapi.YukiHookAPI
import five.ec1cff.scene_freeform.R
import five.ec1cff.scene_freeform.databinding.FragmentHomeBinding
import five.ec1cff.scene_freeform.viewmodels.ModuleStatusViewModel
import five.ec1cff.scene_freeform.viewmodels.RemoteStatus

class HomeFragment: Fragment() {
    private lateinit var binding: FragmentHomeBinding
    private val viewModel by activityViewModels<ModuleStatusViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            title = getString(R.string.home_menu_title)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activated = YukiHookAPI.Status.isModuleActive
        val context = requireContext()
        binding.moduleStatusTitle.text = if (activated) getString(R.string.module_activated)
        else getString(R.string.module_inactivated)
        binding.moduleStatusIcon.setImageDrawable(
            if (activated) AppCompatResources.getDrawable(context, R.drawable.ic_check_circle)
            else AppCompatResources.getDrawable(context, R.drawable.ic_error_circle)
        )
        binding.moduleStatusSummary.text = YukiHookAPI.Status.executorName
        binding.moduleStatus.setCardBackgroundColor(
            MaterialColors.getColor(view,
                if (activated) com.google.android.material.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorError
            )
        )
        if (!activated) {
            binding.systemStatus.visibility = View.GONE
            binding.systemUiStatus.visibility = View.GONE
            viewModel.systemServerStatus.value = RemoteStatus(RemoteStatus.Status.UNKNOWN_ERROR)
            viewModel.systemUIStatus.value = RemoteStatus(RemoteStatus.Status.UNKNOWN_ERROR)
            return
        }
        if (viewModel.systemServerStatus.value == null || viewModel.systemUIStatus.value == null)
            viewModel.checkRemoteStatus()
        viewModel.systemServerStatus.observe(viewLifecycleOwner) {
            binding.systemStatusSummary.text = when (it.status) {
                RemoteStatus.Status.INJECTED -> context.getString(R.string.remote_injected, it.version)
                RemoteStatus.Status.CHECKING -> context.getString(R.string.remote_checking)
                RemoteStatus.Status.VERSION_NOT_MATCH -> context.getString(R.string.remote_version_mismatch, it.version)
                RemoteStatus.Status.UNKNOWN_ERROR -> context.getString(R.string.remote_not_injected)
            }
            binding.systemStatus.setCardBackgroundColor(
                MaterialColors.getColor(binding.systemStatus,
                    when (it.status) {
                        RemoteStatus.Status.VERSION_NOT_MATCH, RemoteStatus.Status.UNKNOWN_ERROR ->
                            com.google.android.material.R.attr.colorError
                        else -> com.google.android.material.R.attr.colorSecondary
                    }
                )
            )
            binding.systemStatusIcon.setImageDrawable(
                AppCompatResources.getDrawable(context, when (it.status) {
                    RemoteStatus.Status.INJECTED -> R.drawable.ic_check_circle
                    RemoteStatus.Status.CHECKING -> R.drawable.ic_updating
                    else -> R.drawable.ic_error_circle
                })
            )
        }
        viewModel.systemUIStatus.observe(viewLifecycleOwner) {
            binding.systemUiStatusSummary.text = when (it.status) {
                RemoteStatus.Status.INJECTED -> context.getString(R.string.remote_injected, it.version)
                RemoteStatus.Status.CHECKING -> context.getString(R.string.remote_checking)
                RemoteStatus.Status.VERSION_NOT_MATCH -> context.getString(R.string.remote_version_mismatch, it.version)
                RemoteStatus.Status.UNKNOWN_ERROR -> context.getString(R.string.remote_not_injected)
            }
            binding.systemUiStatus.setCardBackgroundColor(
                MaterialColors.getColor(binding.systemUiStatus,
                    when (it.status) {
                        RemoteStatus.Status.VERSION_NOT_MATCH, RemoteStatus.Status.UNKNOWN_ERROR ->
                            com.google.android.material.R.attr.colorError
                        else -> com.google.android.material.R.attr.colorTertiary
                    }
                )
            )
            binding.systemUiStatusIcon.setImageDrawable(
                AppCompatResources.getDrawable(context, when (it.status) {
                    RemoteStatus.Status.INJECTED -> R.drawable.ic_check_circle
                    RemoteStatus.Status.CHECKING -> R.drawable.ic_updating
                    else -> R.drawable.ic_error_circle
                })
            )
        }
    }
}
