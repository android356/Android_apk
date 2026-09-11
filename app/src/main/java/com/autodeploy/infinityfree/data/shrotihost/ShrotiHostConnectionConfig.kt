package com.autodeploy.infinityfree.data.shrotihost

data class ShrotiHostConnectionConfig(
    val server: String,
    val port: Int = 21,
    val username: String,
    val password: String,
    val remoteRootDirectory: String = "/public_html/",
    val useFtps: Boolean = true,
    val timeoutMillis: Int = 20000
)
