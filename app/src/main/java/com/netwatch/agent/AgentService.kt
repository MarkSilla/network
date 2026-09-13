package com.netwatch.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AgentService : Service() {

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var running = false

    private val prefs by lazy {
        getSharedPreferences("agent", Context.MODE_PRIVATE)
    }

    private val agentId: String
        get() = prefs.getString("agent_id", null) ?: run {
            val id = "android-" + System.currentTimeMillis()
            prefs.edit().putString("agent_id", id).apply()
            id
        }

    private val dashboardUrl: String
        get() = prefs.getString("dashboard", "")?.trimEnd('/') ?: ""

    private val routerIp: String
        get() = prefs.getString("router", "192.168.100.1")
            ?: "192.168.100.1"

    private val agentKey: String
        get() = prefs.getString("key", "") ?: ""

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            1001,
            createNotification("Starting NetWatch Agent...")
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (!running) {
            running = true

            serviceScope.launch {
                agentLoop()
            }
        }

        return START_STICKY
    }

    private suspend fun agentLoop() {

        while (running) {

            try {

                if (dashboardUrl.isBlank()) {
                    updateNotification(
                        "ERROR • Dashboard URL is empty"
                    )
                    delay(5000)
                    continue
                }

                if (agentKey.isBlank()) {
                    updateNotification(
                        "ERROR • Agent Key is empty"
                    )
                    delay(5000)
                    continue
                }

                updateNotification(
                    "Connecting to dashboard..."
                )

                val registerResult = registerAgent()

                if (!registerResult.success) {

                    updateNotification(
                        "ERROR • ${registerResult.message}"
                    )

                    delay(5000)
                    continue
                }

                updateNotification(
                    "CONNECTED • Dashboard OK"
                )

                val devices = scanLocalNetwork()

                val sendResult = sendDevices(devices)

                if (!sendResult.success) {

                    updateNotification(
                        "CONNECTED • Upload failed: ${sendResult.message}"
                    )

                } else {

                    updateNotification(
                        "CONNECTED • ${devices.length()} devices found"
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

    private data class RequestResult(
        val success: Boolean,
        val message: String
    )

    private fun registerAgent(): RequestResult {

        val body = JSONObject()

        body.put("agentId", agentId)
        body.put("routerIp", routerIp)
        body.put("agentKey", agentKey)
        body.put("platform", "android")
        body.put("version", "1.0.0")

        return postJson(
            "$dashboardUrl/api/agent/register",
            body
        )
    }

    private fun sendDevices(
        devices: JSONArray
    ): RequestResult {

        val body = JSONObject()

        body.put("agentId", agentId)
        body.put("routerIp", routerIp)
        body.put("agentKey", agentKey)
        body.put("devices", devices)

        return postJson(
            "$dashboardUrl/api/agent/devices",
            body
        )
    }

    private fun postJson(
        endpoint: String,
        body: JSONObject
    ): RequestResult {

        var connection: HttpURLConnection? = null

        return try {

            val url = URL(endpoint)

            connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(
                    body.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode =
                connection.responseCode

            val stream =
                if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val responseText =
                stream?.use {
                    BufferedReader(
                        InputStreamReader(it)
                    ).readText()
                } ?: ""

            if (responseCode in 200..299) {

                RequestResult(
                    true,
                    "HTTP $responseCode"
                )

            } else {

                val message =
                    when (responseCode) {
                        401 -> "Unauthorized"
                        403 -> "INVALID AGENT KEY"
                        404 -> "API endpoint not found"
                        500 -> "Dashboard server error"
                        else -> "HTTP $responseCode"
                    }

                RequestResult(
                    false,
                    message
                )
            }

        } catch (e: Exception) {

            RequestResult(
                false,
                e.message ?: "Connection failed"
            )

        } finally {

            connection?.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiIpAddress(): String? {

        val wifiManager =
            applicationContext.getSystemService(
                Context.WIFI_SERVICE
            ) as? WifiManager
                ?: return null

        val ip =
            wifiManager.connectionInfo.ipAddress

        if (ip == 0) return null

        return listOf(
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        ).joinToString(".")
    }

    @Suppress("DEPRECATION")
    private fun getNetmask(): String {

        val wifiManager =
            applicationContext.getSystemService(
                Context.WIFI_SERVICE
            ) as? WifiManager
                ?: return "255.255.255.0"

        val mask =
            wifiManager.dhcpInfo?.netmask ?: 0

        if (mask == 0) {
            return "255.255.255.0"
        }

        return listOf(
            mask and 0xff,
            mask shr 8 and 0xff,
            mask shr 16 and 0xff,
            mask shr 24 and 0xff
        ).joinToString(".")
    }

    private fun ipToLong(ip: String): Long {

        val parts = ip.split(".")

        return (
            (parts[0].toLong() shl 24) or
            (parts[1].toLong() shl 16) or
            (parts[2].toLong() shl 8) or
            parts[3].toLong()
        ) and 0xffffffffL
    }

    private fun longToIp(value: Long): String {

        return listOf(
            (value shr 24) and 255,
            (value shr 16) and 255,
            (value shr 8) and 255,
            value and 255
        ).joinToString(".")
    }

    private fun calculateSubnet(
        ip: String,
        netmask: String
    ): Pair<Long, Long> {

        val ipLong = ipToLong(ip)
        val maskLong = ipToLong(netmask)

        val network = ipLong and maskLong

        val broadcast =
            network or
                (maskLong.inv() and 0xffffffffL)

        return Pair(network, broadcast)
    }

    private suspend fun scanLocalNetwork(): JSONArray =
        withContext(Dispatchers.IO) {

            val result = JSONArray()

            val phoneIp =
                getWifiIpAddress()
                    ?: return@withContext result

            val netmask = getNetmask()

            val subnet =
                calculateSubnet(
                    phoneIp,
                    netmask
                )

            val network = subnet.first
            val broadcast = subnet.second

            val totalHosts =
                broadcast - network - 1

            if (totalHosts > 1024) {
                return@withContext scanUsingArpOnly(
                    result,
                    phoneIp
                )
            }

            val executor =
                Executors.newFixedThreadPool(24)

            try {

                val futures =
                    mutableListOf<
                        java.util.concurrent.Future<*>
                    >()

                var address = network + 1

                while (address < broadcast) {

                    val targetIp =
                        longToIp(address)

                    if (targetIp != phoneIp) {

                        futures += executor.submit {

                            if (
                                isHostReachable(
                                    targetIp
                                )
                            ) {

                                addDevice(
                                    result,
                                    targetIp,
                                    phoneIp
                                )
                            }
                        }
                    }

                    address++
                }

                futures.forEach { future ->

                    try {

                        future.get(
                            1500,
                            TimeUnit.MILLISECONDS
                        )

                    } catch (_: Exception) {
                    }
                }

                executor.shutdown()

                executor.awaitTermination(
                    8,
                    TimeUnit.SECONDS
                )

            } finally {

                executor.shutdownNow()
            }

            readArpTable(
                result,
                phoneIp
            )

            if (
                routerIp.isNotBlank() &&
                routerIp != phoneIp &&
                isHostReachable(routerIp)
            ) {

                addDevice(
                    result,
                    routerIp,
                    phoneIp,
                    true
                )
            }

            result
        }

    private fun scanUsingArpOnly(
        result: JSONArray,
        phoneIp: String
    ): JSONArray {

        readArpTable(
            result,
            phoneIp
        )

        return result
    }

    private fun isHostReachable(
        ip: String
    ): Boolean {

        try {

            val address =
                InetAddress.getByName(ip)

            if (address.isReachable(180)) {
                return true
            }

        } catch (_: Exception) {
        }

        val ports = intArrayOf(
            80,
            443,
            8080,
            22,
            53,
            445,
            139
        )

        for (port in ports) {

            try {

                Socket().use { socket ->

                    socket.connect(
                        InetSocketAddress(
                            ip,
                            port
                        ),
                        180
                    )

                    return true
                }

            } catch (_: Exception) {
            }
        }

        return false
    }

    private fun readArpTable(
        result: JSONArray,
        phoneIp: String
    ) {

        try {

            val process =
                Runtime.getRuntime().exec(
                    arrayOf(
                        "cat",
                        "/proc/net/arp"
                    )
                )

            val reader =
                BufferedReader(
                    InputStreamReader(
                        process.inputStream
                    )
                )

            reader.useLines { lines ->

                lines.drop(1).forEach { line ->

                    val parts =
                        line.trim()
                            .split(
                                Regex("\\s+")
                            )

                    if (parts.size >= 4) {

                        val ip = parts[0]
                        val mac = parts[3]

                        if (
                            ip != phoneIp &&
                            mac !=
                                "00:00:00:00:00:00" &&
                            mac.contains(":")
                        ) {

                            addDevice(
                                result,
                                ip,
                                phoneIp,
                                ip == routerIp,
                                mac
                            )
                        }
                    }
                }
            }

        } catch (_: Exception) {
        }
    }

    private fun addDevice(
        result: JSONArray,
        ip: String,
        phoneIp: String,
        isRouter: Boolean = false,
        macAddress: String = "Unknown"
    ) {

        for (i in 0 until result.length()) {

            val existing =
                result.optJSONObject(i)

            if (
                existing?.optString(
                    "ipAddress"
                ) == ip
            ) {

                if (
                    macAddress != "Unknown" &&
                    existing.optString(
                        "macAddress"
                    ) == "Unknown"
                ) {

                    existing.put(
                        "macAddress",
                        macAddress
                    )
                }

                return
            }
        }

        val device = JSONObject()

        device.put(
            "id",
            "$ip-$macAddress"
        )

        device.put(
            "name",
            if (isRouter) {
                "Router $ip"
            } else {
                "Unknown $ip"
            }
        )

        device.put(
            "ipAddress",
            ip
        )

        device.put(
            "macAddress",
            macAddress
        )

        device.put(
            "deviceType",
            if (isRouter) "Router"
            else "Unknown"
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
            "discoverySource",
            "android-lan-scan"
        )

        result.put(device)
    }

    private fun createNotification(
        text: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            "netwatch_agent"
        )
            .setContentTitle(
                "NetWatch Android Agent"
            )
            .setContentText(text)
            .setSmallIcon(
                android.R.drawable.ic_dialog_info
            )
            .setOngoing(true)
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
            1001,
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
                    "netwatch_agent",
                    "NetWatch Agent",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onDestroy() {

        running = false
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
