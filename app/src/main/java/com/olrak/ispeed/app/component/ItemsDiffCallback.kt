package com.olrak.ispeed.app.component

import androidx.recyclerview.widget.DiffUtil

open class ListItemPayloadDiffCallback :  DiffUtil.Callback() {

    var oldItems: List<Any> = emptyList()

    var newItems: List<Any> = emptyList()

    override fun getOldListSize() = oldItems.size

    override fun getNewListSize() = newItems.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldItems[oldItemPosition] as? ListItemWithPayload
        val newItem = newItems[newItemPosition] as? ListItemWithPayload

        return when {
            oldItem != null && newItem != null -> oldItem.id == newItem.id
            else -> oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldItems[oldItemPosition] as? ListItemWithPayload
        val newItem = newItems[newItemPosition] as? ListItemWithPayload

        return when {
            oldItem != null && newItem != null -> oldItem.payload == newItem.payload
            else -> oldItems[oldItemPosition] == newItems[newItemPosition]
        }
    }
}

interface ListItemWithPayload {
    val id: Any
    val payload: Any
}