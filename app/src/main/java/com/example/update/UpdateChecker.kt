package com.example.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UpdateChecker:
 * Verifies application version against target 1.3.5 and displays
 * 'Phantom Nexus System Patch' modal window if an update is required,
 * preventing further usage until resolved.
 */
class UpdateChecker(private val context: Context) {

    companion object {
        const val TARGET_VERSION = "1.3.5"
        const val PREFS_NAME = "phantom_nexus_update_prefs"
        const val KEY_PATCH_RESOLVED = "patch_v135_resolved"
        const val KEY_INSTALLED_VERSION = "installed_version"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isUpdateRequired = MutableStateFlow(false)
    val isUpdateRequired: StateFlow<Boolean> = _isUpdateRequired.asStateFlow()

    private val _currentVersion = MutableStateFlow(TARGET_VERSION)
    val currentVersion: StateFlow<String> = _currentVersion.asStateFlow()

    private val _isPatchApplied = MutableStateFlow(false)
    val isPatchApplied: StateFlow<Boolean> = _isPatchApplied.asStateFlow()

    init {
        val isResolved = prefs.getBoolean(KEY_PATCH_RESOLVED, false)
        val installedVer = prefs.getString(KEY_INSTALLED_VERSION, "1.0.0") ?: "1.0.0"

        _isPatchApplied.value = isResolved
        _currentVersion.value = if (isResolved) TARGET_VERSION else installedVer

        // If patch has not been resolved or installed version is lower than 1.3.5, update is required!
        _isUpdateRequired.value = !isResolved || (installedVer != TARGET_VERSION)
        Log.i("UpdateChecker", "Initialized. isUpdateRequired=${_isUpdateRequired.value}, isResolved=$isResolved")
    }

    /**
     * Re-verifies version against TARGET_VERSION (1.3.5)
     */
    fun checkVersionUpdate(): Boolean {
        val isResolved = prefs.getBoolean(KEY_PATCH_RESOLVED, false)
        val requiresUpdate = !isResolved || (_currentVersion.value != TARGET_VERSION)
        _isUpdateRequired.value = requiresUpdate
        return requiresUpdate
    }

    /**
     * Resolves the required patch and unlocks the application for full usage.
     */
    fun resolvePatch() {
        prefs.edit()
            .putBoolean(KEY_PATCH_RESOLVED, true)
            .putString(KEY_INSTALLED_VERSION, TARGET_VERSION)
            .apply()

        _currentVersion.value = TARGET_VERSION
        _isPatchApplied.value = true
        _isUpdateRequired.value = false
        Log.i("UpdateChecker", "Phantom Nexus System Patch 1.3.5 successfully resolved. System unlocked.")
    }

    /**
     * Manually triggers update check or tests the patch gatekeeper.
     */
    fun triggerMandatoryPatchCheck() {
        _isUpdateRequired.value = true
    }
}
