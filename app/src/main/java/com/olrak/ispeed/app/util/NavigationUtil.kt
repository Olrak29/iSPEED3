package com.olrak.ispeed.app.util

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import javax.inject.Inject

class NavigationUtil @Inject constructor() {

    fun navigateActivity(
        context: Activity,
        className: Class<*>?,
        bundle: Bundle? = null,
        requestCode: Int? = null,
        willFinish: Boolean = false,
        willFinishActivities: Boolean = false
    ) {
        Intent(context, className).apply {
            bundle?.let { putExtras(it) }
            if (requestCode != null) context.startActivityForResult(this, requestCode)
            else context.startActivity(this)
            if (willFinish) context.finish()
            else if (willFinishActivities) context.finishAffinity()
        }
    }
}