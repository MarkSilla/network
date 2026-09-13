package com.netwatch.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URLEncoder
import java.util.UUID
import kotlin.concurrent.thread

class AgentService : Service() {

    companion object {
        const val ACTION_STATUS = "com.netwatch.agent.STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_FOUND = "found"

        private const val CHANNEL_ID = "netwatch_agent"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var running = false
    }

    private var dashboardUrl = ""
    private var routerIp = ""
    private var agentKey = ""
    private var agentId = ""
    private var hostname = ""

    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "NetWatch Agent",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        startForeground(
            NOTIFICATION_ID,
            createNotification("NetWatch Agent running")
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        dashboardUrl = intent?.getStringExtra("dashboardUrl")
            ?.trim()
            ?.trimEnd('/')
            ?: ""

        routerIp = intent?.getStringExtra("routerIp")
            ?.trim()
            ?: ""

        agentKey = intent?.getStringExtra("agentKey")
            ?.trim()
            ?: ""

        agentId = intent?.getStringExtra("agentId")
            ?.trim()
            ?: "android-${UUID.randomUUID()}"

        hostname = android.os.Build.MODEL ?: "Android Agent"

        if (!running) {
            running = true

            thread {
                runAgent()
            }
        }

        return START_STICKY
    }

    private fun runAgent() {

        // Register immediately.
        sendStatus(
            status = "Connecting to dashboard...",
            progress = 0,
            total = 254,
            found = 0
        )

        registerAgent()

        while (running) {

            val devices = mutableListOf<DeviceInfo>()

            sendStatus(
                status = "Scanning local network...",
                progress = 0,
                total = 254,
                found = 0
            )

            // Scan the local /24 network.
            for (host in 1..254) {

                if (!running) break

                val ip = buildIp(routerIp, host)

                if (ip != null) {
                    try {
                        if (isReachable(ip)) {
                            val device = DeviceInfo(
                                name = ip,
                                ipAddress = ip,
                                macAddress = "",
                                deviceType = "LAN Device",
                                vendor = "",
                                connectionStatus = "ACTIVE",
                                accessStatus = "ALLOWED",
                                latency = 0,
                                hostname = ""
                            )

                            if (devices.none { it.ipAddress == ip }) {
                                devices.add(device)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val progress = host

                sendStatus(
                    status = "Scanning local network...",
                    progress = progress,
                    total = 254,
                    found = devices.size
                )

                // Small delay so the phone is not overloaded.
                Thread.sleep(25)
            }

            sendStatus(
                status = "Scan complete",
                progress = 254,
                total = 254,
                found = devices.size
            )

            // Report discovered devices.
            if (running) {
                reportDevices(devices)
            }

            // Keep the agent alive / online.
            if (running) {
                registerAgent()
            }

            // Wait before the next scan.
            for (i in 1..50) {
                if (!running) break
                Thread.sleep(100)
            }
        }
    }

    private fun registerAgent() {

        try {
            val url = buildUrl(
                "/api/agent/register",
                mapOf(
                    "agentId" to agentId,
                    "routerIp" to routerIp,
                    "agentKey" to agentKey,
                    "hostname" to hostname
                )
            )

            val result = getRequest(url)

            if (result.first in 200..299) {
                sendStatus(
                    status = "Agent connected",
                    progress = 0,
                    total = 254,
                    found = 0
                )
            }

        } catch (_: Exception) {
            sendStatus(
                status = "Dashboard connection error",
                progress = 0,
                total = 254,
                found = 0
            )
        }
    }

    private fun reportDevices(devices: List<DeviceInfo>) {

        if (devices.isEmpty()) {
            return
        }

        try {

            /*
             * Current dashboard has a GET fallback endpoint.
             * Send one device at a time to keep the URL small.
             */
            for (device in devices) {

                if (!running) break

                val url = buildUrl(
                    "/api/agent/device",
                    mapOf(
                        "agentId" to agentId,
                        "routerIp" to routerIp,
                        "agentKey" to agentKey,
                        "name" to device.name,
                        "ipAddress" to device.ipAddress,
                        "macAddress" to device.macAddress,
                        "deviceType" to device.deviceType,
                        "vendor" to device.vendor,
                        "connectionStatus" to device.connectionStatus,
                        "accessStatus" to device.accessStatus,
                        "latency" to device.latency.toString(),
                        "hostname" to device.hostname
                    )
                )

                getRequest(url)
            }

        } catch (_: Exception) {
        }
    }

    private fun isReachable(ip: String): Boolean {

        return try {
            val address = InetAddress.getByName(ip)

            // Android's isReachable can be unreliable for ICMP,
            // but this keeps the scan lightweight.
            address.isReachable(250)

        } catch (_: Exception) {
            false
        }
    }

    private fun buildIp(router: String, host: Int): String? {

        val parts = router.split(".")

        if (parts.size != 4) {
            return null
        }

        return try {
            "${parts[0]}.${parts[1]}.${parts[2]}.$host"
        } catch (_: Exception) {
            null
        }
    }

    private fun buildUrl(
        path: String,
        params: Map<String, String>
    ): String {

        val base = dashboardUrl.trimEnd('/')

        val query = params.entries.joinToString("&") { entry ->
            "${URLEncoder.encode(entry.key, "UTF-8")}=" +
                    URLEncoder.encode(entry.value, "UTF-8")
        }

        return "$base$path?$query"
    }

    private fun getRequest(urlString: String): Pair<Int, String> {

        val connection =
            java.net.URL(urlString).openConnection() as HttpURLConnection

        return try {

            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode

            val stream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body = stream?.bufferedReader()?.use {
                it.readText()
            } ?: ""

            Pair(status, body)

        } finally {
            connection.disconnect()
        }
    }

    private fun sendStatus(
        status: String,
        progress: Int,
        total: Int,
        found: Int
    ) {

        val intent = Intent(ACTION_STATUS)

        intent.setPackage(packageName)

        intent.putExtra(EXTRA_STATUS, status)
        intent.putExtra(EXTRA_PROGRESS, progress)
        intent.putExtra(EXTRA_TOTAL, total)
        intent.putExtra(EXTRA_FOUND, found)

        sendBroadcast(intent)

        updateNotification(
            "$status $progress/$total • Found: $found"
        )
    }

    private fun createNotification(text: String): Notification {

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetWatch Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_wifi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {

        val manager =
            getSystemService(NotificationManager::class.java)

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    data class DeviceInfo(
        val name: String,
        val ipAddress: String,
        val macAddress: String,
        val deviceType: String,
        val vendor: String,
        val connectionStatus: String,
        val accessStatus: String,
        val latency: Int,
        val hostname: String
    )
}
