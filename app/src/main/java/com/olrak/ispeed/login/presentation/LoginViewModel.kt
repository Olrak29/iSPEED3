package com.olrak.ispeed.login.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.olrak.ispeed.login.domain.LoginUserCredentials
import com.olrak.ispeed.app.shared.data.UserDetails
import com.olrak.ispeed.app.shared.domain.FirebaseUseCase
import com.olrak.ispeed.app.shared.extension.coRunCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUserCredentials: LoginUserCredentials,
    private val firebaseAuth: FirebaseAuth,
    private val currentUser: FirebaseUser?,
    private val firebaseUseCase: FirebaseUseCase
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>(ShowLoginNoData)

    val loginState: LiveData<LoginState> get() = _loginState

    val alreadyLoggedIn = MutableLiveData<Boolean>()

    init {
        checkScreenState()
    }

    private fun checkScreenState() {
        viewModelScope.launch {
            alreadyLoggedIn.value = currentUser != null && currentUser.isEmailVerified
        }
    }

    fun loginCredentials(userDetails: UserDetails) {
        viewModelScope.launch {
            updateUIState(state = ShowLoginLoading)

            coRunCatching {
                loginUserCredentials.invoke(userDetails)
            }.onSuccess {
                updateUIState(state = ShowLoginSuccess(firebaseAuth.currentUser?.isEmailVerified))
            }.onFailure {
                updateUIState(state = ShowLoginError(it))
            }

            updateUIState(state = ShowLoginDismissLoading)
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            updateUIState(state = ShowLoginLoading)

            coRunCatching {
                firebaseUseCase.forgotPassword(email = email)
            }.onSuccess {
                updateUIState(state = ShowForgetPasswordSuccess(isSentLink = it))
            }.onFailure {
                updateUIState(state = ShowLoginError(it))
            }

            updateUIState(state = ShowLoginDismissLoading)
        }
    }

    private fun updateUIState(state: LoginState) {
        _loginState.value = state
    }
}