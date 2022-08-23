package five.ec1cff.scene_freeform.fragments

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.highcapable.yukihookapi.hook.log.loggerD
import five.ec1cff.scene_freeform.R
import five.ec1cff.scene_freeform.adapters.AppSelectorAdapter
import five.ec1cff.scene_freeform.config.Constants
import five.ec1cff.scene_freeform.databinding.FragmentAppSelectorListBinding
import five.ec1cff.scene_freeform.viewmodels.AppItem
import five.ec1cff.scene_freeform.viewmodels.AppSelectorViewModel

class AppSelectorFragment : Fragment(), MenuProvider, AppSelectorAdapter.OnCheckedChangedListener {
    private val viewModel by viewModels<AppSelectorViewModel>()
    private lateinit var binding: FragmentAppSelectorListBinding

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_app_selector, menu)
        (menu.findItem(R.id.search_app).actionView as SearchView).apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.query(query)
                    return true
                }
            })
            setOnCloseListener {
                viewModel.query(null)
                false
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == android.R.id.home) {
            findNavController().navigateUp()
        }
        return true
    }

    override fun onCheckedChanged(item: AppItem, checked: Boolean) {
        item.isChecked = checked
        loggerD(msg = "onCheckChanged($checked): $item")
        viewModel.setPackageChecked(item.packageName, checked)
    }

    @Suppress("Unchecked_Cast")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val type = requireArguments().getString(KEY_TYPE)!!
        binding = FragmentAppSelectorListBinding.inflate(inflater, container, false)
        requireActivity().apply {
            addMenuProvider(this@AppSelectorFragment, viewLifecycleOwner, Lifecycle.State.RESUMED)
            this as AppCompatActivity
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = when (type) {
                Constants.APP_JUMP_SCOPE -> getString(R.string.start_activity_scope_title_empty)
                Constants.NOTIFICATION_SCOPE -> getString(R.string.notification_scope_title_empty)
                else -> ""
            }
        }
        val myAdapter = AppSelectorAdapter(this)
        viewModel.filteredAppList.observe(viewLifecycleOwner) {
            myAdapter.update(it)
            binding.refresh.isRefreshing = false
        }
        if (viewModel.filteredAppList.value == null) {
            viewModel.setType(type)
            refresh()
        }
        binding.refresh.setOnRefreshListener {
            refresh()
        }
        with (binding.list) {
            layoutManager = LinearLayoutManager(context)
            adapter = myAdapter
        }
        return binding.root
    }

    private fun refresh() {
        binding.refresh.isRefreshing = true
        viewModel.refresh()
    }

    companion object {
        @JvmStatic
        fun newInstance(columnCount: Int) =
            AppSelectorFragment()

        const val KEY_TYPE = "type"
    }
}