package com.olrak.ispeed.app.util

import android.widget.EditText
import com.olrak.ispeed.app.util.Default.Companion.FIELD_REQUIRED
import javax.inject.Inject

class ValidationUtil @Inject constructor() {

    fun validateFields(array: List<EditText>) : Boolean {
        var isValidFields = false
        array.forEach {
            if (it.text.toString().isEmpty()) {
                it.error = FIELD_REQUIRED
            } else {
                isValidFields = true
            }
        }
        return array.all { isValidFields }
    }
}