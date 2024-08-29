package com.olrak.ispeed.app.component

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KClass

class CustomRecyclerView(context: Context, attrs: AttributeSet?) : RecyclerView(context, attrs) {
    val viewAdapter = CustomRecyclerListAdapter()
    
    init {
        adapter = viewAdapter
    }

    inline fun <B : ViewBinding, reified I : Any> addItemBindings(viewHolders: RecyclerViewHolder<B, I>) {
        @Suppress("UNCHECKED_CAST")
        viewAdapter.bindings[calculateItemViewType(I::class)] = viewHolders as RecyclerViewHolder<ViewBinding, Any>
    }

    fun setItems(items: List<Any>?) = viewAdapter.updateItems(items ?: emptyList())

    companion object {
        fun calculateItemViewType(className: KClass<*>): Int = className.hashCode()
    }
}