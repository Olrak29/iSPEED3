package com.olrak.ispeed.login.domain

import com.olrak.ispeed.login.data.LoginRepository
import com.olrak.ispeed.app.shared.data.UserDetails
import javax.inject.Inject

class LoginUserCredentials @Inject constructor(
    private val loginRepository: LoginRepository
) {
    suspend operator fun invoke(userDetails: UserDetails) = loginRepository.loginCredentials(userDetails)
}