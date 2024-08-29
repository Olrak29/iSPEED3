package com.olrak.ispeed.app.shared.dto

/**
 * Reusable component
 *
 * Describes data rendered in [com.olrak.ispeed.R.layout.track_internet_list_item]
 * */
data class ListItemViewDto(
    val itemId: Int,
    val firstLine: String? = null,
    val secondLine: String? = null,
    val thirdLine: String? = null,
    val fourthLine: String? = null,
    val fifthLine: String? = null,
    val isStable: Boolean = false
)