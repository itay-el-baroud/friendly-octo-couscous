package com.simplemonitor.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var lastCommandText: TextView
    private lateinit var connectionText: TextView

    private val apiService = ApiService()
    private val handler = Handler(Looper.getMainLooper())
    private val permissionRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        lastCommandText = findViewById(R.id.last_command_text)
        connectionText = findViewById(R.id.connection_text)

        requestAppPermissions()
        startBackgroundLoops()
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), permissionRequestCode)
        }
    }

    private fun startBackgroundLoops() {
        val deviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val deviceInfo = Build.MANUFACTURER + " " + Build.MODEL

        Thread {
            while (true) {
                try {
                    val ok = apiService.sendUserStatus(deviceId, deviceInfo)
                    runOnUiThread {
                        statusText.text = if (ok) "Status: Sent" else "Status: Failed"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(30000)
            }
        }.start()

        Thread {
            while (true) {
                try {
                    val commands = apiService.fetchPendingCommands(deviceId)
                    if (commands.isNotEmpty()) {
                        val last = commands.first()
                        val action = last.optString("action", "no_action")
                        val id = last.optString("id", "no_id")
                        runOnUiThread {
                            lastCommandText.text = "Last Command: $action (id: $id)"
                        }
                        apiService.markCommandExecuted(id)
                    }
                    runOnUiThread {
                        connectionText.text = "Commands: " + commands.size.toString()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(5000)
            }
        }.start()
    }
}
