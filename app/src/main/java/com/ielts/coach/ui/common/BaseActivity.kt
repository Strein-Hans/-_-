package com.ielts.coach.ui.common

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ielts.coach.util.LocaleHelper

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyLocale()
        super.onCreate(savedInstanceState)
    }

    protected fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ── Permission handling ───────────────────────────────────────

    private var pendingPermissions: Array<String>? = null
    private var permissionCode = 0

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            onPermissionsResult(allGranted, permissionCode)
        }

    fun checkAndRequestPermissions(permissions: Array<String>, code: Int) {
        pendingPermissions = permissions
        permissionCode = code

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            onPermissionsResult(true, code)
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    protected open fun onPermissionsResult(granted: Boolean, code: Int) {}

    // ── Locale ────────────────────────────────────────────────────

    private fun applyLocale() {
        LocaleHelper.applySavedLocale(this)
    }
}
