package com.mytvlauncher.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.mytvlauncher.model.AppEntry

class AppLoader(private val pm: PackageManager) {

    fun loadLaunchableApps(): List<AppEntry> {
        val apps = mutableListOf<AppEntry>()
        
        val intent = Intent("android.intent.action.MAIN")
        intent.addCategory("android.intent.category.LAUNCHER")
        
        val resolves = pm.queryIntentActivities(intent, 0)
        
        for (resolveInfo in resolves) {
            try {
                val packageName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                
                apps.add(AppEntry(packageName, label, icon))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return apps.sortedBy { it.label }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    fun getAppLabel(packageName: String): String {
        return try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
