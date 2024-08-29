package com.olrak.ispeed.register.presentation

import android.widget.EditText
import androidx.activity.viewModels
import com.olrak.ispeed.databinding.ActivityRegisterBinding
import com.olrak.ispeed.app.foundation.BaseActivity
import com.olrak.ispeed.app.shared.data.UserDetails
import com.olrak.ispeed.app.shared.extension.setVisible
import com.olrak.ispeed.app.shared.extension.showFancyToast
import com.olrak.ispeed.app.util.Default.Companion.EMAIL_VERIFICATION_MSG
import com.shashank.sony.fancytoastlib.FancyToast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterActivity : BaseActivity<ActivityRegisterBinding>(bindingInflater = ActivityRegisterBinding::inflate) {

    private val viewModel: RegisterViewModel by viewModels()

    override fun onActivityCreated() {
        super.onActivityCreated()
        with(binding) {
            setupComponents()
            setupObserver()
        }
    }

    private fun ActivityRegisterBinding.setupComponents() {
        btnRegister.setOnClickListener {
            validateFields(
                listOf(
                    inputFirstName,
                    inputLastName,
                    inputEmailAddress,
                    inputInstitution,
                    inputPassword,
                    inputConfirmPassword
                )
            ).also { valid ->
                if (valid) submitForm(
                    UserDetails(
                        firstName = inputFirstName.text.toString(),
                        lastName = inputLastName.text.toString(),
                        email = inputEmailAddress.text.toString(),
                        institution = inputInstitution.text.toString(),
                        password = inputPassword.text.toString())
                )
            }
        }
        signinAccount.setOnClickListener {
            finish()
        }
    }

    private fun setupObserver() {
        with(viewModel) {
            registerState.observe(this@RegisterActivity) { state ->
                when(state) {
                    is ShowRegisterLoading -> binding.updateLoadingState(showLoading = true)
                    is ShowRegisterDismissLoading -> binding.updateLoadingState(showLoading = false)
                    is EmailVerificationSuccess -> showFancyToast(EMAIL_VERIFICATION_MSG, duration = FancyToast.LENGTH_LONG)
                    is SaveFireStoreDetailsSuccess -> finish()
                    is ShowRegisterError -> state.throwable.message?.let {
                        showFancyToast(state.throwable.message.toString(), style = FancyToast.ERROR)
                    }
                }
            }
        }
    }

    private fun validateFields(listOfEditText: List<EditText>) = validationUtil.validateFields(listOfEditText)

    private fun submitForm(userDetails: UserDetails) = viewModel.registerCredentials(userDetails)

    private fun ActivityRegisterBinding.updateLoadingState(showLoading: Boolean) = loadingWidget.root.setVisible(canShow = showLoading)
}