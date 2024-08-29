package com.olrak.ispeed.login.presentation

open class LoginState

object ShowLoginNoData : LoginState()

object ShowLoginLoading : LoginState()

object ShowLoginDismissLoading : LoginState()

data class ShowLoginSuccess(val isVerified: Boolean?) : LoginState()

data class ShowForgetPasswordSuccess(val isSentLink: Boolean?) : LoginState()

data class ShowLoginError(val throwable: Throwable) : LoginState()