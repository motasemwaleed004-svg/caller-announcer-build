package dev.callerannouncer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object PermissionGate {
    val requiredRuntimePermissions: Array<String>
        get() = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS
        )

    val optionalRuntimePermissions: Array<String>
        get() = arrayOf(Manifest.permission.READ_CALL_LOG)

    fun missingRequired(context: Context): List<String> = requiredRuntimePermissions.filterNot {
        hasPermission(context, it)
    }

    fun missingOptional(context: Context): List<String> = optionalRuntimePermissions.filterNot {
        hasPermission(context, it)
    }

    fun hasRequiredRuntimePermissions(context: Context): Boolean = missingRequired(context).isEmpty()

    fun hasCallLogPermission(context: Context): Boolean = hasPermission(context, Manifest.permission.READ_CALL_LOG)

    private fun hasPermission(context: Context, permission: String): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
