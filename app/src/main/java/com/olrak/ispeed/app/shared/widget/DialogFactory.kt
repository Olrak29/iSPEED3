package com.olrak.ispeed.app.shared.widget

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.olrak.ispeed.R
import com.olrak.ispeed.app.shared.extension.setVisible
import com.olrak.ispeed.databinding.WidgetCustomInformationDialogBinding
import com.olrak.ispeed.databinding.WidgetCustomReminderDialogBinding
import com.olrak.ispeed.databinding.WidgetCustomResultDialogBinding


class DialogFactory {

    companion object {
        fun showCustomDialog(
            context: Context,
            dialogAttributes: DialogAttributes,
            primaryButtonClicked: (() -> Unit)? = null,
            secondaryButtonClicked: (() -> Unit)? = null
        ) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            WidgetCustomReminderDialogBinding.inflate(inflater).apply {
                val dialog = Dialog(context, R.style.ThemeDialog)
                dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(root)

                data = dialogAttributes
                primaryButton.setOnClickListener {
                    primaryButtonClicked?.invoke()
                    dialog.dismiss()
                }
                secondaryButton.setOnClickListener {
                    secondaryButtonClicked?.invoke()
                    dialog.dismiss()
                }

                dialog.show()
            }
        }

        fun showCustomInfoDialog(
            context: Context,
            dialogAttributes: InformationDialogAttributes
        ) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            WidgetCustomInformationDialogBinding.inflate(inflater).apply {
                val dialog = Dialog(context, R.style.ThemeInfoDialog)
                dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(root)

                data = dialogAttributes
                close.setOnClickListener {
                    dialog.dismiss()
                }

                dialog.show()
            }
        }

        fun showCustomResultDialog(
            context: Context,
            dialogAttributes: ResultDialogAttributes,
            primaryButtonClicked: ((view: View) -> Unit)? = null,
            secondaryButtonClicked: (() -> Unit)? = null
        ) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            WidgetCustomResultDialogBinding.inflate(inflater).apply {
                val dialog = Dialog(context, R.style.ThemeInfoDialog)
                dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(root)

                data = dialogAttributes
                primaryButton.setOnClickListener {
                    buttonLayout.setVisible(canShow = false)
                    primaryButtonClicked?.invoke(this.root)
                    dialog.dismiss()
                }
                secondaryButton.setOnClickListener {
                    secondaryButtonClicked?.invoke()
                    dialog.dismiss()
                }

                dialog.show()
            }
        }
    }

    data class DialogAttributes(
        val title: String? = null,
        val subTitle: String? = null,
        val primaryButtonTitle: String? = null,
        val secondaryButtonTitle: String? = null
    )

    data class InformationDialogAttributes(
        val title: String? = null,
        val header: String? = null,
        val firstLineTitle: String? = null,
        val secondLineTitle: String? = null,
        val thirdLineTitle: String? = null,
    )

    data class ResultDialogAttributes(
        val title: String? = null,
        val firstLineTitle: String? = null,
        val secondLineTitle: String? = null,
        val thirdLineTitle: String? = null,
        val fourthLineTitle: String? = null,
        val fifthLineTitle: String? = null,
        val primaryButtonTitle: String? = null,
        val secondaryButtonTitle: String? = null
    )
}