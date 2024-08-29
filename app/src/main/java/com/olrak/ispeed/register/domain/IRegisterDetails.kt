package com.olrak.ispeed.register.domain

import com.google.firebase.auth.AuthResult

interface IRegisterCredential {
    fun isNetworkSuccess() : Boolean
}

interface ICreateUserCredential : IRegisterCredential {

    val authResult: AuthResult
    override fun isNetworkSuccess() = authResult.user?.uid != null
}

interface ISendEmailVerification : IRegisterCredential {

    val isEmailVerified: Boolean
    override fun isNetworkSuccess() = isEmailVerified
}

interface ISaveDetailsFireStore : IRegisterCredential {

    val isSaveSuccess: Boolean
    override fun isNetworkSuccess() = isSaveSuccess
}