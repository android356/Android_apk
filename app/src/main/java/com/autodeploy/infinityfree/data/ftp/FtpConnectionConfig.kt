package com.autodeploy.infinityfree.data.ftp

data class FtpConnectionConfig(
    val server: String,
    val port: Int = 21,
    val username: String,
    val password: String,
    val remoteRootDirectory: String = "/htdocs/",
    val timeoutMillis: Int = 15000
)
