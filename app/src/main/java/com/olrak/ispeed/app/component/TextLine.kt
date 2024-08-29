package com.olrak.ispeed.app.component

import android.content.Context
import androidx.annotation.StringRes

data class TextLine(
    private val text: String? = null,
    @StringRes private val textRes: Int? = null
) : TextContainer {

    override fun getString(context: Context): String? = when {
        text != null && textRes != null -> "${context.getString(textRes)} $text"
        text != null  -> text
        textRes != null -> context.getString(textRes)
        else -> null
    }

    companion object {
        val EMPTY: TextLine = TextLine()
    }
}

interface TextContainer {
    fun getString(context: Context): String?
}