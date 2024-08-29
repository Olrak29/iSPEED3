package com.olrak.ispeed.app.shared.binder

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.olrak.ispeed.R
import com.olrak.ispeed.app.component.RecyclerViewHolder
import com.olrak.ispeed.app.shared.dto.ListItemViewDto
import com.olrak.ispeed.databinding.TrackInternetListItemBinding

data class TrackListItem(
    val id: Any? = null,
    val dto: ListItemViewDto
)

fun <T : Any>setupTrackListItemBinder(
    context: Context,
    dtoRetriever: (T) -> ListItemViewDto
) = object : RecyclerViewHolder<TrackInternetListItemBinding, T> {

    override fun bind(binder: TrackInternetListItemBinding, item: T) {
        with(binder) {
            val itemDto = dtoRetriever(item)
            dto = itemDto

            if (itemDto.isStable) {
                trackStatus.setTextColor(ContextCompat.getColor(context, R.color.lightGreen))
            } else {
                trackStatus.setTextColor(ContextCompat.getColor(context, R.color.lightRed))
            }

            executePendingBindings()
        }
    }

    override fun inflate(parent: ViewGroup) = TrackInternetListItemBinding.inflate(
        LayoutInflater.from(parent.context), parent, false)
}