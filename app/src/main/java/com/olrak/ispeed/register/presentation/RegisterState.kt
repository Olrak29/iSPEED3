package com.olrak.ispeed.register.presentation

import com.google.firebase.auth.AuthResult

open class RegisterState

object ShowRegisterNoData : RegisterState()

object ShowRegisterLoading : RegisterState()

object ShowRegisterDismissLoading : RegisterState()

object EmailVerificationSuccess : RegisterState()

object SaveFireStoreDetailsSuccess : RegisterState()

data class RegisterCredentialSuccess(val authResult: AuthResult) : RegisterState()

data class ShowRegisterError(val throwable: Throwable) : RegisterState()