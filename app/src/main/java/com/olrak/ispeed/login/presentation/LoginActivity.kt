package com.olrak.ispeed.login.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import com.olrak.ispeed.R
import com.olrak.ispeed.app.foundation.BaseActivity
import com.olrak.ispeed.app.shared.data.UserDetails
import com.olrak.ispeed.app.shared.extension.setVisible
import com.olrak.ispeed.app.shared.extension.showFancyToast
import com.olrak.ispeed.app.shared.widget.DialogFactory
import com.olrak.ispeed.app.shared.widget.DialogFactory.Companion.showCustomDialog
import com.olrak.ispeed.app.util.Default.Companion.EMAIL_NOT_VERIFIED_MSG
import com.olrak.ispeed.app.util.Default.Companion.FORGET_PASSWORD_MSG
import com.olrak.ispeed.databinding.ActivityLoginBinding
import com.olrak.ispeed.main.MainScreenHandler
import com.olrak.ispeed.register.presentation.RegisterActivity
import com.shashank.sony.fancytoastlib.FancyToast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : BaseActivity<ActivityLoginBinding>(bindingInflater = ActivityLoginBinding::inflate) {

    private val viewModel: LoginViewModel by viewModels()

    override fun onActivityCreated() {
        super.onActivityCreated()
        with(binding) {
            setupComponents()
            setupObserver()
        }
    }

    private fun ActivityLoginBinding.setupComponents() {
        // Check push notification permission,
        // this is only necessary for API level >= 33 (TIRAMISU),
        // due to the new Android 13 updates.
        checkNotificationPermission()
        viewUtil.locationPermission(this@LoginActivity)

        btnLogin.setOnClickListener {
            validateFields(
                listOf(inputEmailAddress, inputPassword)
            ).also { valid ->
                if (valid) viewModel.loginCredentials(
                    UserDetails(
                        email = inputEmailAddress.text.toString(),
                        password = inputPassword.text.toString()
                    )
                )
            }
        }

        btnSignup.setOnClickListener {
            goToRegisterScreen()
        }

        tvForgoPassword.setOnClickListener {
            showForgotPasswordField()
        }

        btnSendForgetEmail.setOnClickListener {
            viewModel.forgotPassword(email = inputForgotPasssword.text.toString())
        }
    }

    private fun ActivityLoginBinding.showForgotPasswordField() {
        val visibility = if (tvForgoPasswordLabel.visibility == View.GONE) View.VISIBLE else View.GONE
        tvForgoPasswordLabel.visibility = visibility
        inputForgotPasssword.visibility = visibility
        btnSendForgetEmail.visibility = visibility
    }

    private fun setupObserver() {
        with(viewModel) {
            loginState.observe(this@LoginActivity) { state ->
                when(state){
                    is ShowLoginSuccess -> state.handleSuccess()
                    is ShowLoginLoading -> binding.updateLoadingState(showLoading = true)
                    is ShowLoginDismissLoading -> binding.updateLoadingState(showLoading = false)
                    is ShowLoginError -> binding.updateLoadingState(showLoading = false).also { state.handleError() }
                    is ShowForgetPasswordSuccess -> showFancyToast(FORGET_PASSWORD_MSG, FancyToast.SUCCESS, duration = FancyToast.LENGTH_LONG)
                }
            }

            alreadyLoggedIn.observe(this@LoginActivity) { loggedIn ->
                if (loggedIn) goToDashboardScreen()
            }
        }
    }

    private fun checkNotificationPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    /* context = */ this,
                    /* permission = */ Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            true
        } else false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted.not()) showPermissionDialog()
    }

    private fun showPermissionDialog() {
        showCustomDialog(
            context = this,
            dialogAttributes = DialogFactory.DialogAttributes(
                title = getString(R.string.dialog_permission_required_title),
                subTitle = getString(R.string.dialog_subtitle),
                primaryButtonTitle = getString(R.string.action_cancel),
                secondaryButtonTitle = getString(R.string.action_settings)
            ), secondaryButtonClicked = {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )
    }

    private fun ShowLoginSuccess.handleSuccess() {
        if (isVerified == true) goToDashboardScreen()
        else showFancyToast(EMAIL_NOT_VERIFIED_MSG, FancyToast.INFO, duration = FancyToast.LENGTH_LONG)
    }

    private fun ShowLoginError.handleError() = showFancyToast(throwable.message.toString(), FancyToast.ERROR)

    private fun ActivityLoginBinding.updateLoadingState(showLoading: Boolean) = loadingWidget.root.setVisible(canShow = showLoading)

    private fun goToRegisterScreen() = navigationUtil.navigateActivity(this, RegisterActivity::class.java)

    private fun goToDashboardScreen() = navigationUtil.navigateActivity(this, MainScreenHandler::class.java).also { finish() }

    private fun validateFields(listOfEditText: List<EditText>) = validationUtil.validateFields(listOfEditText)
}