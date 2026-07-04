package com.lpu.personalnotesapplication.util

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

/**
 * Simple helper to check and request a single permission when needed.
 * For scoped storage via SAF this is usually not required, but older SDKs
 * writing to shared storage may need WRITE_EXTERNAL_STORAGE (< Q).
 */
object PermissionHelper {
    fun isPermissionGranted(activity: Activity, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun needsStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    @Composable
    fun rememberRequestPermissionLauncher(onResult: (Boolean) -> Unit): ActivityResultLauncher<String> {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val activity = ctx as ComponentActivity
        val launcher = remember {
            activity.activityResultRegistry.register("request_permission",
                ActivityResultContracts.RequestPermission(), onResult)
        }
        return launcher
    }
}
