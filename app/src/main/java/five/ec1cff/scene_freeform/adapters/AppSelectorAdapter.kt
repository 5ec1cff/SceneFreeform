package five.ec1cff.scene_freeform.adapters

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import five.ec1cff.scene_freeform.GlideApp
import five.ec1cff.scene_freeform.databinding.FragmentAppSelectorBinding
import five.ec1cff.scene_freeform.viewmodels.AppItem

class AppSelectorAdapter(private val checkedSet: HashSet<String>) : RecyclerView.Adapter<AppSelectorAdapter.ViewHolder>() {
    private var mList = listOf<AppItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        return ViewHolder(
            FragmentAppSelectorBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    }

    fun update(newList: List<AppItem>) {
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = mList.size

            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(op: Int, np: Int) =
                mList[op].packageName == newList[np].packageName

            override fun areContentsTheSame(op: Int, np: Int) =
                mList[op] == newList[np]
        }).let {
            mList = newList
            it.dispatchUpdatesTo(this)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        val item = mList[position]
        val packageName = item.packageName
        binding.appName.text = item.label
        binding.appDesc.text = packageName
        binding.itemCheckbox.setOnCheckedChangeListener { _, b ->
            if (b) checkedSet.add(packageName)
            else checkedSet.remove(packageName)
        }
        binding.itemCheckbox.isChecked = packageName in checkedSet
        GlideApp.with(binding.appIcon)
            .load(item.packageInfo)
            .into(binding.appIcon)
    }

    override fun getItemCount(): Int = mList.size

    inner class ViewHolder(val binding: FragmentAppSelectorBinding) :
        RecyclerView.ViewHolder(binding.root) {
            init {
                binding.itemRoot.setOnClickListener {
                    binding.itemCheckbox.toggle()
                }
            }
    }

}