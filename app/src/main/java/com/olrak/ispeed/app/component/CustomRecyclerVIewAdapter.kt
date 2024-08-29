package com.olrak.ispeed.app.component

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.olrak.ispeed.app.component.CustomRecyclerView.Companion.calculateItemViewType

open class ViewHolder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)

class CustomRecyclerListAdapter : RecyclerView.Adapter<ViewHolder>() {

    private val items: MutableList<Any> = ArrayList()

    val bindings: HashMap<Int, RecyclerViewHolder<ViewBinding, Any>> = hashMapOf()

    private fun getBinding(viewType: Int) = bindings[viewType] ?: run {
        val itemType = items.first { calculateItemViewType(it::class) == viewType }::class.simpleName
        throw IllegalStateException("item binding is not found: $itemType ?")
    }

    fun updateItems(items: List<Any>) {
        val diffUtil = ListItemPayloadDiffCallback().apply {
            oldItems = this@CustomRecyclerListAdapter.items
            newItems = items
        }

        val diffResult = DiffUtil.calculateDiff(diffUtil)
        this.items.clear()
        this.items.addAll(items)

        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(getBinding(viewType).inflate(parent))

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = calculateItemViewType(className = items[position]::class)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = getBinding(
        viewType = calculateItemViewType(
            className = items[position]::class
        )
    ).bind(holder.binding, items[position])
}