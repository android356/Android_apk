package com.autodeploy.infinityfree.data.deployment

sealed class ProviderResult<out T> {
    data class Success<out T>(val data: T, val message: String = "Operation successful") : ProviderResult<T>()
    data class Error(
        val message: String,
        val errorCode: Int = -1,
        val isAuthError: Boolean = false,
        val isNetworkError: Boolean = false,
        val isPathError: Boolean = false,
        val cause: Throwable? = null
    ) : ProviderResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
}
