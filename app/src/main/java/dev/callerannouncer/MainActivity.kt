package dev.callerannouncer

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CallerSpeaker.warmUp(applicationContext)
        setContentView(createContentView())
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatus()
    }

    private fun createContentView(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(TextView(this).apply {
            text = "Caller Announcer"
            textSize = 24f
            gravity = Gravity.CENTER
        }, matchWrap())

        statusView = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(18), 0, dp(18))
        }
        container.addView(statusView, matchWrap())

        container.addView(button("Grant required permissions") { requestRequiredPermissions() }, matchWrap())
        container.addView(button("Set as Call Screening app") { requestCallScreeningRole() }, matchWrap())
        container.addView(button("Try grant Call Log fallback") { requestOptionalCallLogPermission() }, matchWrap())
        container.addView(button("Test: Mo3men") { CallerSpeaker.speakName(this, "Mo3men") }, matchWrap())
        container.addView(button("Test: Me 3") { CallerSpeaker.speakName(this, "Me 3") }, matchWrap())
        container.addView(button("Test: number") { CallerSpeaker.speakDigits(this, "+201001234567") }, matchWrap())
        container.addView(button("Allow unrestricted battery use") { requestBatteryOptimizationExemption() }, matchWrap())
        container.addView(button("Open this app's settings") { openAppSettings() }, matchWrap())

        container.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(18), 0, 0)
            text = "Behavior:\n" +
                "- Saved contacts speak after 1200ms.\n" +
                "- Standalone digits in saved names stay digits: Me 3 stays Me 3.\n" +
                "- Franco inside words converts: Mo3men becomes مؤمن.\n" +
                "- Unsaved visible numbers are spoken digit by digit.\n" +
                "- Private or hidden numbers stay silent."
        }, matchWrap())

        return ScrollView(this).apply { addView(container) }
    }

    private fun updateStatus() {
        val missingRequired = PermissionGate.missingRequired(this)
        val callLogStatus = if (PermissionGate.hasCallLogPermission(this)) "granted" else "not granted / may be restricted"
        val batteryStatus = if (isIgnoringBatteryOptimizations()) "allowed" else "not allowed yet"
        val screeningStatus = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "not available before Android 10"
            !isCallScreeningRoleAvailable() -> "not available on this device"
            isCallScreeningRoleHeld() -> "enabled"
            else -> "not enabled"
        }

        statusView.text = buildString {
            append("Required permissions: ")
            append(if (missingRequired.isEmpty()) "granted" else "missing ${missingRequired.joinToString { it.substringAfterLast('.') }}")
            append('\n')
            append("Call Screening role: $screeningStatus")
            append('\n')
            append("Optional Call Log fallback: $callLogStatus")
            append('\n')
            append("Battery optimization exemption: $batteryStatus")
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val missing = PermissionGate.missingRequired(this)
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1001)
    }

    private fun requestOptionalCallLogPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val missing = PermissionGate.missingOptional(this)
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1002)
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) return
        startActivityForResult(
            roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
            1003
        )
    }

    private fun isCallScreeningRoleAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun isCallScreeningRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations()) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        bottomMargin = (8 * resources.displayMetrics.density).toInt()
    }
}
