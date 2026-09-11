package com.autodeploy.infinityfree.service

data class FileSystemEvent(
    val relativePath: String,
    val absolutePath: String,
    val eventMask: Int,
    val isDirectory: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
