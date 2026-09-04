package com.autodeploy.infinityfree.data.ftp

sealed class FtpResult<out T> {
    data class Success<out T>(val data: T) : FtpResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : FtpResult<Nothing>()
}
