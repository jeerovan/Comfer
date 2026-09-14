package com.jeerovan.comfer

internal fun AppInfo.searchIdentity(): String =
    "${componentName?.flattenToString() ?: packageName}:${user?.hashCode()}"

internal fun uniqueSearchApps(apps: List<AppInfo>): List<AppInfo> = apps.distinctBy { it.searchIdentity() }
