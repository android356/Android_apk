package com.autodeploy.infinityfree.data.saf

import android.net.Uri

data class SafFileItem(
    val displayName: String,
    val relativePath: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)
