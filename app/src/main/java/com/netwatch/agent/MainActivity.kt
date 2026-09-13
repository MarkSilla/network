package com.netwatch.agent

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var dashboardUrlInput: EditText
    private lateinit var routerIpInput: EditText
    private lateinit var agentKeyInput: EditText
    private lateinit var statusText: TextView

    private val prefs by lazy {
        getSharedPreferences("netwatch", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(40, 40, 40, 40)
        root.setBackgroundColor(Color.rgb(10, 18, 32))

        val title = TextView(this)
        title.text = "NetWatch"
        title.textSize = 30f
        title.setTextColor(Color.WHITE)

        val subtitle = TextView(this)
        subtitle.text = "Android Network Agent"
        subtitle.textSize = 16f
        subtitle.setTextColor(Color.LTGRAY)

        dashboardUrlInput = createInput(
            "Dashboard URL",
            prefs.getString(
                "dashboard_url",
                "https://network-device-dashboard-tydeft.v2.appdeploy.ai"
            ) ?: ""
        )

        routerIpInput = createInput(
            "Router IP",
            prefs.getString("router_ip", "192.168.100.1") ?: ""
        )

        agentKeyInput = createInput(
            "Agent Key",
            prefs.getString("agent_key", "") ?: ""
        )

        statusText = TextView(this)
        statusText.text = "Status: Not connected"
        statusText.textSize = 16f
        statusText.setTextColor(Color.WHITE)
        statusText.setPadding(0, 30, 0, 30)

        val testButton = Button(this)
        testButton.text = "TEST DASHBOARD CONNECTION"

        val startButton = Button(this)
        startButton.text = "START AGENT"

        val stopButton = Button(this)
        stopButton.text = "STOP AGENT"

        root.addView(title)
        root.addView(subtitle)
        root.addView(dashboardUrlInput)
        root.addView(routerIpInput)
        root.addView(agentKeyInput)
        root.addView(statusText)
        root.addView(testButton)
        root.addView(startButton)
        root.addView(stopButton)

        setContentView(root)

        testButton.setOnClickListener {
            testConnection()
        }

        startButton.setOnClickListener {
            startAgent()
        }

        stopButton.setOnClickListener {
            stopAgent()
        }
    }

    private fun createInput(
        hint: String,
        value: String
    ): EditText {
        val input = EditText(this)

        input.hint = hint
        input.setText(value)
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)

        input.setPadding(0, 20, 0, 20)

        return input
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("dashboard_url", dashboardUrlInput.text.toString().trim())
            .putString("router_ip", routerIpInput.text.toString().trim())
            .putString("agent_key", agentKeyInput.text.toString().trim())
            .apply()
    }

    private fun testConnection() {
        saveSettings()

        val dashboardUrl = dashboardUrlInput.text.toString().trim()
        val routerIp = routerIpInput.text.toString().trim()
        val agentKey = agentKeyInput.text.toString().trim()

        if (dashboardUrl.isEmpty() ||
            routerIp.isEmpty() ||
            agentKey.isEmpty()
        ) {
            statusText.text = "Status: Please complete all fields."
            return
        }

        statusText.text = "Status: Connecting..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                registerAgent(
                    dashboardUrl,
                    routerIp,
                    agentKey
                )
            }

            statusText.text = result
        }
    }

    private fun registerAgent(
        dashboardUrl: String,
        routerIp: String,
        agentKey: String
    ): String {

        return try {
            val agentId = getAgentId()

            val baseUrl = dashboardUrl.trimEnd('/')

            /*
             * AppDeploy's CDN currently rejects POST requests from
             * native clients. The Android agent therefore uses the
             * GET registration fallback.
             */
            val urlString =
                "$baseUrl/api/agent/register" +
                        "?agentId=${encode(agentId)}" +
                        "&routerIp=${encode(routerIp)}" +
                        "&agentKey=${encode(agentKey)}" +
                        "&hostname=${encode(android.os.Build.MODEL)}"

            val connection =
                URL(urlString).openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode

            val responseText =
                try {
                    connection.inputStream.bufferedReader().use {
                        it.readText()
                    }
                } catch (_: Exception) {
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""
                }

            connection.disconnect()

            when {
                responseCode in 200..299 ->
                    "Status: CONNECTED\nDashboard accepted the Android Agent."

                responseCode == 403 ->
                    "Status: ERROR\nServer still blocks this request (403)."

                responseCode == 404 ->
                    "Status: ERROR\nAgent endpoint not found (404)."

                responseCode == 400 ->
                    "Status: ERROR\nInvalid agent information (400)."

                else ->
                    "Status: ERROR\nHTTP $responseCode\n$responseText"
            }

        } catch (e: Exception) {
            "Status: CONNECTION FAILED\n${e.message ?: "Unknown error"}"
        }
    }

    private fun startAgent() {
        saveSettings()

        val dashboardUrl = dashboardUrlInput.text.toString().trim()
        val routerIp = routerIpInput.text.toString().trim()
        val agentKey = agentKeyInput.text.toString().trim()

        if (dashboardUrl.isEmpty() ||
            routerIp.isEmpty() ||
            agentKey.isEmpty()
        ) {
            statusText.text = "Status: Please complete all fields."
            return
        }

        val intent =
            android.content.Intent(this, AgentService::class.java)

        intent.putExtra("dashboard_url", dashboardUrl)
        intent.putExtra("router_ip", routerIp)
        intent.putExtra("agent_key", agentKey)

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        statusText.text =
            "Status: AGENT RUNNING\nScanning local network..."
    }

    private fun stopAgent() {
        val intent =
            android.content.Intent(this, AgentService::class.java)

        stopService(intent)

        statusText.text = "Status: Agent stopped."
    }

    private fun getAgentId(): String {
        val existing = prefs.getString("agent_id", null)

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

    private fun encode(value: String): String {
        return java.net.URLEncoder
            .encode(value, "UTF-8")
    }
}
