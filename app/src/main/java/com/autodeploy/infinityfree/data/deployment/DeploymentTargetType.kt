package com.autodeploy.infinityfree.data.deployment

enum class DeploymentTargetType(val id: String, val displayName: String, val shortName: String) {
    INFINITY_FREE("INFINITY_FREE", "InfinityFree Hosting", "InfinityFree"),
    SHROTI_HOST("SHROTI_HOST", "ShrotiHost cPanel Hosting", "ShrotiHost"),
    GITHUB("GITHUB", "GitHub Repository", "GitHub");

    companion object {
        fun fromId(id: String?): DeploymentTargetType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: INFINITY_FREE
        }
    }
}
