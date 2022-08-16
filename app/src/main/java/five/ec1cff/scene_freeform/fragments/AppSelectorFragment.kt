package five.ec1cff.scene_freeform.fragments

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import five.ec1cff.scene_freeform.R
import five.ec1cff.scene_freeform.adapters.AppSelectorAdapter
import five.ec1cff.scene_freeform.databinding.FragmentAppSelectorListBinding
import five.ec1cff.scene_freeform.viewmodels.AppSelectorViewModel

class AppSelectorFragment : Fragment(), MenuProvider {
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
        if (menuItem.itemId == R.id.save_app_list) {
            val key = arguments?.getString(KEY_RESULT_KEY) ?: return true
            setFragmentResult(key, bundleOf(KEY_SELECTED_APPS to viewModel.checkedSet))
            findNavController().navigateUp()
        }
        return true
    }

    @Suppress("Unchecked_Cast")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAppSelectorListBinding.inflate(inflater, container, false)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        val myAdapter = AppSelectorAdapter(viewModel.checkedSet)
        (arguments?.getSerializable(KEY_SELECTED_APPS) as? HashSet<String>)?.also {
            viewModel.checkedSet.addAll(it)
        }
        viewModel.filteredAppList.observe(viewLifecycleOwner) {
            myAdapter.update(it)
            binding.refresh.isRefreshing = false
        }
        if (viewModel.filteredAppList.value == null) {
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

        const val KEY_SELECTED_APPS = "selected_apps"
        const val KEY_RESULT_KEY = "result_key"
    }
}