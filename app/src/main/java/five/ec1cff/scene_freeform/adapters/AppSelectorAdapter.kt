package five.ec1cff.scene_freeform.adapters

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import five.ec1cff.scene_freeform.GlideApp
import five.ec1cff.scene_freeform.databinding.FragmentAppSelectorBinding
import five.ec1cff.scene_freeform.viewmodels.AppItem

class AppSelectorAdapter(private val listener: OnCheckedChangedListener) : RecyclerView.Adapter<AppSelectorAdapter.ViewHolder>() {
    private var mList = listOf<AppItem>()

    interface OnCheckedChangedListener {
        fun onCheckedChanged(name: String, checked: Boolean)
    }

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
        binding.itemCheckbox.isChecked = item.isChecked
        binding.itemCheckbox.setOnCheckedChangeListener { _, b ->
            listener.onCheckedChanged(packageName, b)
        }
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
                binding.itemCheckbox.setOnCheckedChangeListener { compoundButton, b ->  }
            }
    }

}