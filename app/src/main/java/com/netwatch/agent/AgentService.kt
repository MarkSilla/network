package com.netwatch.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class AgentService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var agentJob: Job? = null

    private val prefs by lazy {
        getSharedPreferences("netwatch", Context.MODE_PRIVATE)
    }

    private val channelId = "netwatch_agent"

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            1001,
            createNotification("NetWatch Agent starting...")
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val dashboardUrl =
            intent?.getStringExtra("dashboard_url")
                ?: prefs.getString("dashboard_url", "")
                ?: ""

        val routerIp =
            intent?.getStringExtra("router_ip")
                ?: prefs.getString("router_ip", "")
                ?: ""

        val agentKey =
            intent?.getStringExtra("agent_key")
                ?: prefs.getString("agent_key", "")
                ?: ""

        prefs.edit()
            .putString("dashboard_url", dashboardUrl)
            .putString("router_ip", routerIp)
            .putString("agent_key", agentKey)
            .apply()

        agentJob?.cancel()

        agentJob = serviceScope.launch {

            while (isActive) {

                try {

                    val agentId = getAgentId()

                    val registered =
                        registerAgent(
                            dashboardUrl,
                            routerIp,
                            agentKey,
                            agentId
                        )

                    if (registered) {

                        val devices =
                            discoverDevices(routerIp)

                        reportDevices(
                            dashboardUrl,
                            routerIp,
                            agentKey,
                            agentId,
                            devices
                        )

                        updateNotification(
                            "CONNECTED • ${devices.size} devices found"
                        )

                    } else {

                        updateNotification(
                            "ERROR • Dashboard connection failed"
                        )
                    }

                } catch (e: Exception) {

                    updateNotification(
                        "ERROR • ${e.message ?: "Unknown error"}"
                    )
                }

                delay(5000)
            }
        }

        return START_STICKY
    }

    private fun registerAgent(
        dashboardUrl: String,
        routerIp: String,
        agentKey: String,
        agentId: String
    ): Boolean {

        val url =
            "${dashboardUrl.trimEnd('/')}/api/agent/register" +
                    "?agentId=${encode(agentId)}" +
                    "&routerIp=${encode(routerIp)}" +
                    "&agentKey=${encode(agentKey)}" +
                    "&hostname=${encode(Build.MODEL)}"

        val response =
            getRequest(url)

        return response.first in 200..299
    }

    private fun reportDevices(
        dashboardUrl: String,
        routerIp: String,
        agentKey: String,
        agentId: String,
        devices: List<DeviceInfo>
    ) {

        /*
         * Device data is encoded as a JSON string inside the GET
         * request because the CDN does not currently accept POST.
         */

        val devicesJson =
            buildDevicesJson(devices)

        val url =
            "${dashboardUrl.trimEnd('/')}/api/agent/devices" +
                    "?agentId=${encode(agentId)}" +
                    "&routerIp=${encode(routerIp)}" +
                    "&agentKey=${encode(agentKey)}" +
                    "&devices=${encode(devicesJson)}"

        val response =
            getRequest(url)

        if (response.first !in 200..299) {
            throw Exception(
                "Device report failed: HTTP ${response.first}"
            )
        }
    }

    private fun getRequest(
        urlString: String
    ): Pair<Int, String> {

        val connection =
            URL(urlString).openConnection() as HttpURLConnection

        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        return try {

            val status =
                connection.responseCode

            val stream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body =
                stream?.bufferedReader()?.use {
                    it.readText()
                } ?: ""

            Pair(status, body)

        } finally {

            connection.disconnect()
        }
    }

    private fun discoverDevices(
        routerIp: String
    ): List<DeviceInfo> {

        val result = mutableListOf<DeviceInfo>()

        /*
         * First add the router.
         */
        try {

            val router =
                InetAddress.getByName(routerIp)

            if (router.isReachable(500)) {

                result.add(
                    DeviceInfo(
                        name = "Router",
                        ipAddress = routerIp,
                        macAddress = "",
                        deviceType = "Router"
                    )
                )
            }

        } catch (_: Exception) {
        }

        /*
         * Best-effort LAN discovery.
         *
         * Android does not provide a guaranteed complete
         * router client list without router-specific APIs.
         */
        val subnet =
            routerIp.substringBeforeLast(".") + "."

        for (i in 1..254) {

            val ip =
                subnet + i

            if (ip == routerIp) {
                continue
            }

            try {

                val address =
                    InetAddress.getByName(ip)

                if (address.isReachable(180)) {

                    result.add(
                        DeviceInfo(
                            name = "LAN Device $ip",
                            ipAddress = ip,
                            macAddress = "",
                            deviceType = "Unknown"
                        )
                    )
                }

            } catch (_: Exception) {
            }
        }

        return result
    }

    private fun buildDevicesJson(
        devices: List<DeviceInfo>
    ): String {

        val items =
            devices.joinToString(",") { device ->

                """
                {
                    "name":"${jsonEscape(device.name)}",
                    "ipAddress":"${jsonEscape(device.ipAddress)}",
                    "macAddress":"${jsonEscape(device.macAddress)}",
                    "deviceType":"${jsonEscape(device.deviceType)}",
                    "vendor":"Unknown",
                    "connectionStatus":"ACTIVE",
                    "accessStatus":"ALLOWED",
                    "latency":0,
                    "hostname":""
                }
                """.trimIndent()
            }

        return "[$items]"
    }

    private fun jsonEscape(
        value: String
    ): String {

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun encode(
        value: String
    ): String {

        return URLEncoder
            .encode(value, "UTF-8")
    }

    private fun getAgentId(): String {

        val existing =
            prefs.getString("agent_id", null)

        if (!existing.isNullOrBlank()) {
            return existing
        }

        val created =
            "android-${UUID.randomUUID()}"

        prefs.edit()
            .putString("agent_id", created)
            .apply()

        return created
    }

    private fun createNotification(
        text: String
    ): Notification {

        return if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            Notification.Builder(
                this,
                channelId
            )
                .setContentTitle("NetWatch")
                .setContentText(text)
                .setSmallIcon(
                    android.R.drawable.ic_menu_info_details
                )
                .setOngoing(true)
                .build()

        } else {

            Notification.Builder(this)
                .setContentTitle("NetWatch")
                .setContentText(text)
                .setSmallIcon(
                    android.R.drawable.ic_menu_info_details
                )
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            1001,
            createNotification(text)
        )
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    channelId,
                    "NetWatch Agent",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {

        agentJob?.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    data class DeviceInfo(
        val name: String,
        val ipAddress: String,
        val macAddress: String,
        val deviceType: String
    )
}
