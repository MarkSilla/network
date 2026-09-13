package com.netwatch.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AgentService : Service() {

    companion object {
        const val ACTION_STATUS = "com.netwatch.agent.STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_FOUND = "found"

        private const val CHANNEL_ID = "netwatch_agent"
        private const val NOTIFICATION_ID = 1001

        private const val DASHBOARD_URL =
            "https://network-device-dashboard-tydeft.v2.appdeploy.ai"

        private const val ROUTER_IP = "192.168.100.1"

        private const val AGENT_KEY =
            "ed30b921-e8b1-4342-867d-6b54b7053e32-01bdbc89-ae0c-48ec-8a1b-2ef86f3b7b6d"

        private const val AGENT_ID = "android-agent"
    }

    private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification("Starting Android Agent...")
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (!running) {
            running = true

            worker = Thread {
                runAgent()
            }

            worker?.start()
        }

        return START_STICKY
    }

    private fun runAgent() {

        try {
            sendStatus(
                status = "Connecting to dashboard...",
                progress = 0,
                total = 254,
                found = 0
            )

            updateNotification("Connecting to dashboard...")

            registerAgent()

            sendStatus(
                status = "Scanning local area network...",
                progress = 0,
                total = 254,
                found = 0
            )

            updateNotification("Scanning local area network...")

            val foundDevices = mutableListOf<String>()

            val baseParts = ROUTER_IP.split(".")

            if (baseParts.size != 4) {
                sendStatus(
                    status = "Invalid router IP",
                    progress = 0,
                    total = 254,
                    found = 0
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

                val ip = "$subnet.$i"

                try {

                    val address = InetAddress.getByName(ip)

                    val reachable =
                        address.isReachable(250)

                    if (reachable) {

                        if (!foundDevices.contains(ip)) {
                            foundDevices.add(ip)
                        }

                        sendStatus(
                            status = "Device found: $ip",
                            progress = i,
                            total = 254,
                            found = foundDevices.size
                        )

                        updateNotification(
                            "Scanning $i/254 • ${foundDevices.size} found"
                        )

                    } else {

                        sendStatus(
                            status = "Scanning $i/254",
                            progress = i,
                            total = 254,
                            found = foundDevices.size
                        )

                        updateNotification(
                            "Scanning $i/254 • ${foundDevices.size} found"
                        )
                    }

                } catch (_: Exception) {

                    sendStatus(
                        status = "Scanning $i/254",
                        progress = i,
                        total = 254,
                        found = foundDevices.size
                    )
                }

                Thread.sleep(25)
            }

            if (running) {

                sendStatus(
                    status = "Scan complete. Reporting devices...",
                    progress = 254,
                    total = 254,
                    found = foundDevices.size
                )

                updateNotification(
                    "Reporting ${foundDevices.size} devices..."
                )

                for (ip in foundDevices) {

                    if (!running) {
                        break
                    }

                    reportDevice(ip)

                    Thread.sleep(100)
                }

                registerAgent()

                sendStatus(
                    status = "Agent running • ${foundDevices.size} devices found",
                    progress = 254,
                    total = 254,
                    found = foundDevices.size
                )

                updateNotification(
                    "Agent running • ${foundDevices.size} devices found"
                )

                while (running) {

                    Thread.sleep(5000)

                    if (running) {
                        registerAgent()

                        sendStatus(
                            status = "Agent running • ${foundDevices.size} devices found",
                            progress = 254,
                            total = 254,
                            found = foundDevices.size
                        )
                    }
                }
            }

        } catch (e: Exception) {

            sendStatus(
                status = "Agent error: ${e.message ?: "Unknown error"}",
                progress = 0,
                total = 254,
                found = 0
            )

            updateNotification(
                "Agent error"
            )
        }
    }

    private fun registerAgent() {

        try {

            val url =
                "$DASHBOARD_URL/api/agent/register" +
                        "?agentId=${encode(AGENT_ID)}" +
                        "&routerIp=${encode(ROUTER_IP)}" +
                        "&agentKey=${encode(AGENT_KEY)}" +
                        "&hostname=${encode("Android Agent")}"

            httpGet(url)

        } catch (_: Exception) {
            // Keep scanning even if registration temporarily fails.
        }
    }

    private fun reportDevice(ip: String) {

        try {

            val url =
                "$DASHBOARD_URL/api/agent/device" +
                        "?agentId=${encode(AGENT_ID)}" +
                        "&routerIp=${encode(ROUTER_IP)}" +
                        "&agentKey=${encode(AGENT_KEY)}" +
                        "&name=${encode(ip)}" +
                        "&ipAddress=${encode(ip)}" +
                        "&macAddress=${encode("")}" +
                        "&deviceType=${encode("LAN Device")}" +
                        "&vendor=${encode("Unknown")}" +
                        "&connectionStatus=${encode("ONLINE")}" +
                        "&accessStatus=${encode("UNKNOWN")}" +
                        "&latency=${encode("0")}" +
                        "&hostname=${encode(ip)}"

            httpGet(url)

        } catch (_: Exception) {
            // Ignore individual device reporting failures.
        }
    }

    private fun httpGet(urlString: String): String? {

        return try {

            val url = URL(urlString)

            val connection =
                url.openConnection() as java.net.HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.useCaches = false

            val responseCode =
                connection.responseCode

            val stream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val result =
                stream?.bufferedReader()?.use {
                    it.readText()
                }

            connection.disconnect()

            result

        } catch (_: Exception) {
            null
        }
    }

    private fun encode(value: String): String {
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

                setPackage(packageName)

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
            .setContentTitle("NetWatch Agent")
            .setContentText(text)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

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

            manager.createNotificationChannel(channel)
        }
    }

    private fun stopAgent() {

        running = false

        try {
            worker?.interrupt()
        } catch (_: Exception) {
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
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
