package com.mytvlauncher.model

import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isFavorite: Boolean = false
)

data class FolderEntry(
    val name: String,
    val apps: List<AppEntry>
)
