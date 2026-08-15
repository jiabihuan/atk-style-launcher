package com.mytvlauncher.service

import android.accessibilityservice.AccessibilityService
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent
import android.content.Intent
import android.provider.Settings

class HomeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        // Service connected
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events
    }

    override fun onInterrupt() {
        // Interrupt handling
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun hasPermissions(): Boolean {
        return true
    }

    fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(homeIntent)
    }

    fun openNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun openPowerMenu() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
