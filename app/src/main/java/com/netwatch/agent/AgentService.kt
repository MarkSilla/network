package com.netwatch.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

class AgentService : Service() {

    companion object {
        const val START = "START"
        private const val CHANNEL = "netwatch_agent"
    }

    private var running = false
    private lateinit var agentId: String

    override fun onCreate() {
        super.onCreate()

        agentId = getSharedPreferences("agent", MODE_PRIVATE)
            .getString("agentId", null)
            ?: UUID.randomUUID().toString().also {
                getSharedPreferences("agent", MODE_PRIVATE)
                    .edit()
                    .putString("agentId", it)
                    .apply()
            }

        createChannel()
        startForeground(
            1001,
            notification("NetWatch agent is running")
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!running) {
            running = true

            thread(name = "netwatch-agent") {
                while (running) {
                    try {
                        tick()
                    } catch (_: Exception) {
                    }

                    Thread.sleep(5000)
                }
            }
        }

        return START_STICKY
    }

    private fun tick() {
        val prefs = getSharedPreferences("agent", MODE_PRIVATE)

        val dashboard = prefs
            .getString("dashboard", "")
            ?.trim()
            ?.trimEnd('/')
            ?: return

        val router = prefs
            .getString("router", "")
            ?.trim()
            ?: return

        val key = prefs
            .getString("key", "")
            ?.trim()
            ?: return

        if (dashboard.isEmpty() || router.isEmpty() || key.isEmpty()) {
            return
        }

        val devices = readNeighbors()

        postJson(
            "$dashboard/api/agent/register",
            """{"agentId":"${esc(agentId)}","routerIp":"${esc(router)}","hostname":"${esc(Build.MODEL)}","agentKey":"${esc(key)}"}"""
        )

        val body = buildString {
            append(
                """{"agentId":"${esc(agentId)}","routerIp":"${esc(router)}","agentKey":"${esc(key)}","devices":["""
            )

            devices.forEachIndexed { i, device ->
                if (i > 0) {
                    append(',')
                }

                append(device)
            }

            append("]}")
        }

        postJson(
            "$dashboard/api/agent/devices",
            body
        )
    }

    private fun readNeighbors(): List<String> {
        val result = mutableListOf<String>()
        val file = java.io.File("/proc/net/arp")

        if (!file.exists()) {
            return result
        }

        BufferedReader(file.reader()).useLines { lines ->
            lines.drop(1).forEach { line ->
                val parts = line
                    .trim()
                    .split(Regex("\\s+"))

                if (parts.size >= 4 && parts[0] != "0.0.0.0") {
                    val ip = parts[0]
                    val mac = parts[3].uppercase()

                    if (
                        mac != "00:00:00:00:00:00" &&
                        mac.contains(":")
                    ) {
                        result.add(
                            """{"id":"${esc(ip)}-${esc(mac)}","name":"Device ${esc(ip)}","ipAddress":"${esc(ip)}","macAddress":"${esc(mac)}","deviceType":"Unknown","vendor":"Unknown","connectionStatus":"ACTIVE","accessStatus":"ALLOWED","latency":0}"""
                        )
                    }
                }
            }
        }

        return result.distinct()
    }

    private fun postJson(
        url: String,
        body: String
    ) {
        val connection =
            URL(url).openConnection() as HttpURLConnection

        connection.requestMethod = "POST"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.doOutput = true

        connection.setRequestProperty(
            "Content-Type",
            "application/json"
        )

        connection.outputStream.use {
            it.write(body.toByteArray(Charsets.UTF_8))
        }

        try {
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }

    private fun esc(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "NetWatch Agent",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun notification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("NetWatch Android Agent")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_wifi)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("NetWatch Android Agent")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_wifi)
                .build()
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
