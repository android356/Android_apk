package com.autodeploy.infinityfree.data.deployment

data class DeploymentVerificationInfo(
    val remotePath: String,
    val verified: Boolean,
    val remoteSize: Long? = null,
    val remoteSha: String? = null,
    val message: String = ""
)
