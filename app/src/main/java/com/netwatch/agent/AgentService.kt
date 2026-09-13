package com.netwatch.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

class AgentService : Service() {

    companion object {

        const val ACTION_STATUS =
            "com.netwatch.agent.STATUS"

        const val EXTRA_STATUS =
            "status"

        const val EXTRA_PROGRESS =
            "progress"

        const val EXTRA_TOTAL =
            "total"

        const val EXTRA_FOUND =
            "found"

        private const val CHANNEL_ID =
            "netwatch_agent"

        private const val NOTIFICATION_ID =
            1001

        private const val DEFAULT_DASHBOARD_URL =
            "https://network-device-dashboard-tydeft.v2.appdeploy.ai"
    }

    private var running = false

    private var worker: Thread? = null

    private var dashboardUrl =
        DEFAULT_DASHBOARD_URL

    private var routerIp = ""

    private var agentKey = ""

    private var agentId = ""

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification(
                "Starting Android Agent..."
            )
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        dashboardUrl =
            intent?.getStringExtra(
                "dashboardUrl"
            )
                ?.trim()
                ?.trimEnd('/')
                ?: DEFAULT_DASHBOARD_URL

        routerIp =
            intent?.getStringExtra(
                "routerIp"
            )
                ?.trim()
                ?: ""

        agentKey =
            intent?.getStringExtra(
                "agentKey"
            )
                ?.trim()
                ?: ""

        agentId =
            intent?.getStringExtra(
                "agentId"
            )
                ?.trim()
                ?: "android-agent"

        if (!running) {

            running = true

            worker =
                Thread {
                    runAgent()
                }

            worker?.start()
        }

        return START_STICKY
    }

    private fun runAgent() {

        try {

            if (
                routerIp.isBlank() ||
                agentKey.isBlank()
            ) {

                sendStatus(
                    "Agent configuration missing",
                    0,
                    254,
                    0
                )

                stopAgent()

                return
            }

            sendStatus(
                "Connecting to dashboard...",
                0,
                254,
                0
            )

            updateNotification(
                "Connecting to dashboard..."
            )

            val connectionCode =
                registerAgent()

            if (connectionCode !in 200..299) {

                sendStatus(
                    "Dashboard connection HTTP $connectionCode",
                    0,
                    254,
                    0
                )

                updateNotification(
                    "Connection failed HTTP $connectionCode"
                )

                stopAgent()

                return
            }

            sendStatus(
                "🟢 Agent paired • Dashboard connected",
                0,
                254,
                0
            )

            updateNotification(
                "Agent paired • Dashboard connected"
            )

            sendStatus(
                "Scanning local area network...",
                0,
                254,
                0
            )

            updateNotification(
                "Scanning local network..."
            )

            val foundDevices =
                mutableListOf<String>()

            val baseParts =
                routerIp.split(".")

            if (baseParts.size != 4) {

                sendStatus(
                    "Invalid router IP",
                    0,
                    254,
                    0
                )

                stopAgent()

                return
            }

            val subnet =
                "${baseParts[0]}.${baseParts[1]}.${baseParts[2]}"

            for (i in 1..254) {

                if (!running) {
                    break
                }

                val ip =
                    "$subnet.$i"

                try {

                    val address =
                        InetAddress.getByName(ip)

                    val reachable =
                        address.isReachable(250)

                    if (reachable) {

                        if (
                            !foundDevices.contains(ip)
                        ) {

                            foundDevices.add(ip)
                        }

                        sendStatus(
                            "Device found: $ip",
                            i,
                            254,
                            foundDevices.size
                        )

                        updateNotification(
                            "Scanning $i/254 • ${foundDevices.size} found"
                        )

                    } else {

                        sendStatus(
                            "Scanning $i/254",
                            i,
                            254,
                            foundDevices.size
                        )
                    }

                } catch (_: Exception) {

                    sendStatus(
                        "Scanning $i/254",
                        i,
                        254,
                        foundDevices.size
                    )
                }

                Thread.sleep(25)
            }

            if (!running) {
                return
            }

            sendStatus(
                "Scan complete • ${foundDevices.size} devices",
                254,
                254,
                foundDevices.size
            )

            updateNotification(
                "Scan complete • ${foundDevices.size} devices"
            )

            val uploadCode =
                reportDevices(foundDevices)

            if (uploadCode !in 200..299) {

                sendStatus(
                    "Device upload failed • HTTP $uploadCode",
                    254,
                    254,
                    foundDevices.size
                )

                updateNotification(
                    "Device upload failed • HTTP $uploadCode"
                )

            } else {

                sendStatus(
                    "Upload complete • ${foundDevices.size} devices",
                    254,
                    254,
                    foundDevices.size
                )

                updateNotification(
                    "Upload complete • ${foundDevices.size} devices"
                )
            }

            Thread.sleep(3000)

            while (running) {

                Thread.sleep(5000)

                if (!running) {
                    break
                }

                val heartbeatCode =
                    registerAgent()

                if (
                    heartbeatCode in 200..299
                ) {

                    sendStatus(
                        "🟢 Agent running • ${foundDevices.size} devices",
                        254,
                        254,
                        foundDevices.size
                    )

                    updateNotification(
                        "Agent online • ${foundDevices.size} devices"
                    )

                } else {

                    sendStatus(
                        "Agent heartbeat HTTP $heartbeatCode",
                        254,
                        254,
                        foundDevices.size
                    )

                    updateNotification(
                        "Heartbeat HTTP $heartbeatCode"
                    )
                }
            }

        } catch (e: Exception) {

            sendStatus(
                "Agent error: ${e.message ?: "Unknown error"}",
                0,
                254,
                0
            )

            updateNotification(
                "Agent error"
            )
        }
    }

    private fun registerAgent(): Int {

        return try {

            val url =
                "$dashboardUrl/api/agent/pairing-status" +
                        "?agentId=${encode(agentId)}" +
                        "&agentKey=${encode(agentKey)}" +
                        "&hostname=${encode("Android Agent")}" +
                        "&routerIp=${encode(routerIp)}" +
                        "&_t=${System.currentTimeMillis()}"

            val result =
                httpGetWithCode(url)

            val code =
                result.first

            val body =
                result.second

            if (code !in 200..299) {
                return code
            }

            if (body.isNullOrBlank()) {
                return 502
            }

            val trimmed =
                body.trim()

            if (!trimmed.startsWith("{")) {
                return 502
            }

            val json =
                try {
                    JSONObject(trimmed)
                } catch (_: Exception) {
                    return 502
                }

            val registered =
                json.optBoolean(
                    "registered",
                    false
                )

            val paired =
                json.optBoolean(
                    "paired",
                    false
                )

            if (!registered || !paired) {
                return 403
            }

            200

        } catch (_: Exception) {

            -1
        }
    }

    private fun reportDevices(
        ips: List<String>
    ): Int {

        return try {

            val devices =
                JSONArray()

            for (ip in ips) {

                val device =
                    JSONObject()

                device.put(
                    "name",
                    ip
                )

                device.put(
                    "ipAddress",
                    ip
                )

                device.put(
                    "macAddress",
                    ""
                )

                device.put(
                    "deviceType",
                    "LAN Device"
                )

                device.put(
                    "vendor",
                    "Unknown"
                )

                device.put(
                    "connectionStatus",
                    "ACTIVE"
                )

                device.put(
                    "accessStatus",
                    "ALLOWED"
                )

                device.put(
                    "latency",
                    0
                )

                device.put(
                    "hostname",
                    ip
                )

                devices.put(device)
            }

            val body =
                JSONObject()

            body.put(
                "agentId",
                agentId
            )

            body.put(
                "agentKey",
                agentKey
            )

            body.put(
                "routerIp",
                routerIp
            )

            body.put(
                "devices",
                devices
            )

            val result =
                httpPostWithCode(
                    "$dashboardUrl/api/agent/devices",
                    body.toString()
                )

            result.first

        } catch (_: Exception) {

            -1
        }
    }

    private fun httpGetWithCode(
        urlString: String
    ): Pair<Int, String?> {

        return try {

            val url =
                URL(urlString)

            val connection =
                url.openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                10000

            connection.readTimeout =
                10000

            connection.useCaches =
                false

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Cache-Control",
                "no-cache, no-store, max-age=0"
            )

            connection.setRequestProperty(
                "Pragma",
                "no-cache"
            )

            val responseCode =
                connection.responseCode

            val stream =
                if (
                    responseCode in 200..299
                ) {

                    connection.inputStream

                } else {

                    connection.errorStream
                }

            val result =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }

            connection.disconnect()

            Pair(
                responseCode,
                result
            )

        } catch (e: Exception) {

            Pair(
                -1,
                e.message
            )
        }
    }

    private fun httpPostWithCode(
        urlString: String,
        body: String
    ): Pair<Int, String?> {

        return try {

            val url =
                URL(urlString)

            val connection =
                url.openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                10000

            connection.readTimeout =
                10000

            connection.useCaches =
                false

            connection.doOutput =
                true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Cache-Control",
                "no-cache, no-store, max-age=0"
            )

            connection.setRequestProperty(
                "Pragma",
                "no-cache"
            )

            connection.outputStream
                .bufferedWriter(Charsets.UTF_8)
                .use { writer ->

                    writer.write(body)

                    writer.flush()
                }

            val responseCode =
                connection.responseCode

            val stream =
                if (
                    responseCode in 200..299
                ) {

                    connection.inputStream

                } else {

                    connection.errorStream
                }

            val result =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }

            connection.disconnect()

            Pair(
                responseCode,
                result
            )

        } catch (e: Exception) {

            Pair(
                -1,
                e.message
            )
        }
    }

    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }

    private fun sendStatus(
        status: String,
        progress: Int,
        total: Int,
        found: Int
    ) {

        val intent =
            Intent(ACTION_STATUS).apply {

                setPackage(
                    packageName
                )

                putExtra(
                    EXTRA_STATUS,
                    status
                )

                putExtra(
                    EXTRA_PROGRESS,
                    progress
                )

                putExtra(
                    EXTRA_TOTAL,
                    total
                )

                putExtra(
                    EXTRA_FOUND,
                    found
                )
            }

        sendBroadcast(intent)
    }

    private fun createNotification(
        text: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "NetWatch Agent"
            )
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable.ic_dialog_info
            )
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "NetWatch Agent",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "NetWatch Android network scanning agent"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun stopAgent() {

        running = false

        try {
            worker?.interrupt()
        } catch (_: Exception) {
        }

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {

        running = false

        try {
            worker?.interrupt()
        } catch (_: Exception) {
        }

        worker = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
