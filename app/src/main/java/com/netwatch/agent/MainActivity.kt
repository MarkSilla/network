package com.netwatch.agent

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var dashboardUrlInput: EditText
    private lateinit var routerIpInput: EditText
    private lateinit var agentKeyInput: EditText
    private lateinit var statusText: TextView
    private lateinit var testButton: Button

    private var isTesting = false

    private val prefs by lazy {
        getSharedPreferences("netwatch_agent", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs.getString("agent_id", null) == null) {
            prefs.edit()
                .putString("agent_id", UUID.randomUUID().toString())
                .apply()
        }

        buildUI()
    }

    private fun buildUI() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 18, 32))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val scrollView = ScrollView(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "NetWatch"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        content.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val subtitle = TextView(this).apply {
            text = "Android Network Agent"
            textSize = 15f
            setTextColor(Color.rgb(100, 220, 160))
            setPadding(0, dp(4), 0, dp(20))
        }

        content.addView(subtitle)

        dashboardUrlInput = createInput(
            "Dashboard URL",
            prefs.getString("dashboard_url", "")
                ?: ""
        )

        content.addView(dashboardUrlInput)

        routerIpInput = createInput(
            "Router IP",
            prefs.getString("router_ip", "")
                ?: ""
        )

        content.addView(routerIpInput)

        agentKeyInput = createInput(
            "Agent Key",
            prefs.getString("agent_key", "")
                ?: ""
        )

        content.addView(agentKeyInput)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
            )
            setBackgroundColor(Color.rgb(20, 34, 50))
        }

        val statusLabel = TextView(this).apply {
            text = "CONNECTION STATUS"
            textSize = 12f
            setTextColor(Color.rgb(140, 160, 180))
            setTypeface(null, Typeface.BOLD)
        }

        statusCard.addView(statusLabel)

        statusText = TextView(this).apply {
            text = "Disconnected"
            textSize = 16f
            setTextColor(Color.rgb(255, 180, 80))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        }

        statusCard.addView(statusText)

        val cardParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        cardParams.setMargins(
            0,
            dp(20),
            0,
            dp(16)
        )

        content.addView(statusCard, cardParams)

        testButton = createButton(
            "TEST DASHBOARD CONNECTION"
        )

        testButton.setOnClickListener {
            testConnection()
        }

        content.addView(testButton)

        val startButton = createButton(
            "START AGENT"
        )

        startButton.setOnClickListener {
            startAgent()
        }

        content.addView(startButton)

        val stopButton = createButton(
            "STOP AGENT"
        )

        stopButton.setOnClickListener {
            stopAgent()
        }

        content.addView(stopButton)

        val info = TextView(this).apply {
            text =
                "Keep this phone connected to the same Wi-Fi network as the devices you want NetWatch to discover."
            textSize = 13f
            setTextColor(Color.rgb(150, 165, 180))
            setPadding(
                0,
                dp(20),
                0,
                dp(10)
            )
        }

        content.addView(info)

        scrollView.addView(content)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun createInput(
        hint: String,
        value: String
    ): EditText {

        return EditText(this).apply {

            setText(value)

            this.hint = hint

            textSize = 15f

            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(120, 140, 160))

            setSingleLine(true)

            setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(12)
            )

            setBackgroundColor(
                Color.rgb(25, 42, 60)
            )

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )

            params.setMargins(
                0,
                0,
                0,
                dp(12)
            )

            layoutParams = params
        }
    }

    private fun createButton(
        textValue: String
    ): Button {

        return Button(this).apply {

            text = textValue

            textSize = 13f

            setTextColor(Color.WHITE)

            setTypeface(null, Typeface.BOLD)

            setBackgroundColor(
                Color.rgb(22, 150, 95)
            )

            isAllCaps = false

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )

            params.setMargins(
                0,
                0,
                0,
                dp(10)
            )

            layoutParams = params
        }
    }

    private fun testConnection() {

        val dashboardUrl =
            dashboardUrlInput.text.toString()
                .trim()
                .removeSuffix("/")

        val routerIp =
            routerIpInput.text.toString()
                .trim()

        val agentKey =
            agentKeyInput.text.toString()
                .trim()

        if (dashboardUrl.isEmpty()) {
            showStatus(
                "ERROR • Dashboard URL is empty",
                false
            )
            return
        }

        if (routerIp.isEmpty()) {
            showStatus(
                "ERROR • Router IP is empty",
                false
            )
            return
        }

        if (agentKey.isEmpty()) {
            showStatus(
                "ERROR • Agent Key is empty",
                false
            )
            return
        }

        saveSettings(
            dashboardUrl,
            routerIp,
            agentKey
        )

        if (isTesting) return

        isTesting = true
        testButton.isEnabled = false

        showStatus(
            "CONNECTING...",
            null
        )

        lifecycleScope.launch {

            val result = withContext(Dispatchers.IO) {
                registerAgent(
                    dashboardUrl,
                    routerIp,
                    agentKey
                )
            }

            isTesting = false
            testButton.isEnabled = true

            val success =
                result.startsWith("CONNECTED")

            showStatus(
                result,
                success
            )

            Toast.makeText(
                this@MainActivity,
                result,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun registerAgent(
        baseUrl: String,
        routerIp: String,
        key: String
    ): String {

        var connection: HttpURLConnection? = null

        return try {

            val agentId =
                prefs.getString(
                    "agent_id",
                    null
                ) ?: UUID.randomUUID().toString()

            prefs.edit()
                .putString(
                    "agent_id",
                    agentId
                )
                .apply()

            val url = URL(
                "$baseUrl/api/agent/register"
            )

            connection =
                url.openConnection()
                        as HttpURLConnection

            connection.requestMethod = "POST"

            connection.connectTimeout = 10000

            connection.readTimeout = 10000

            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val body =
                JSONObject().apply {

                    put(
                        "agentId",
                        agentId
                    )

                    put(
                        "routerIp",
                        routerIp
                    )

                    put(
                        "agentKey",
                        key
                    )

                    put(
                        "hostname",
                        android.os.Build.MODEL
                    )
                }.toString()

            connection.outputStream.use { output ->

                output.write(
                    body.toByteArray(
                        Charsets.UTF_8
                    )
                )

                output.flush()
            }

            val responseCode =
                connection.responseCode

            val responseBody =
                try {

                    val stream =
                        if (responseCode >= 400) {
                            connection.errorStream
                        } else {
                            connection.inputStream
                        }

                    stream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }
                        ?: ""

                } catch (_: Exception) {
                    ""
                }

            if (responseCode in 200..299) {

                "CONNECTED • Dashboard OK"

            } else {

                val serverMessage =
                    parseServerMessage(
                        responseBody
                    )

                when (responseCode) {

                    401 ->
                        "ERROR 401 • Unauthorized\n$serverMessage"

                    403 ->
                        "ERROR 403 • Server rejected request\n$serverMessage"

                    404 ->
                        "ERROR 404 • Register endpoint not found\n$serverMessage"

                    408 ->
                        "ERROR 408 • Request timeout\n$serverMessage"

                    in 500..599 ->
                        "ERROR $responseCode • Server error\n$serverMessage"

                    else ->
                        "ERROR $responseCode\n$serverMessage"
                }
            }

        } catch (e: java.net.UnknownHostException) {

            "ERROR • Cannot find dashboard server"

        } catch (e: java.net.ConnectException) {

            "ERROR • Cannot connect to dashboard"

        } catch (e: java.net.SocketTimeoutException) {

            "ERROR • Connection timeout"

        } catch (e: Exception) {

            "ERROR • ${e.javaClass.simpleName}: ${e.message}"

        } finally {

            connection?.disconnect()
        }
    }

    private fun parseServerMessage(
        responseBody: String
    ): String {

        if (responseBody.isBlank()) {
            return "No response body from server"
        }

        return try {

            val json =
                JSONObject(responseBody)

            json.optString(
                "error",
                json.optString(
                    "message",
                    responseBody
                )
            )

        } catch (_: Exception) {

            responseBody.take(500)
        }
    }

    private fun saveSettings(
        dashboardUrl: String,
        routerIp: String,
        agentKey: String
    ) {

        prefs.edit()
            .putString(
                "dashboard_url",
                dashboardUrl
            )
            .putString(
                "router_ip",
                routerIp
            )
            .putString(
                "agent_key",
                agentKey
            )
            .apply()
    }

    private fun startAgent() {

        saveSettings(
            dashboardUrlInput.text.toString()
                .trim()
                .removeSuffix("/"),

            routerIpInput.text.toString()
                .trim(),

            agentKeyInput.text.toString()
                .trim()
        )

        try {

            val intent =
                Intent(
                    this,
                    AgentService::class.java
                )

            startForegroundService(intent)

            showStatus(
                "AGENT STARTING...",
                null
            )

            Toast.makeText(
                this,
                "Android Agent started",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            showStatus(
                "ERROR • ${e.message}",
                false
            )
        }
    }

    private fun stopAgent() {

        try {

            val intent =
                Intent(
                    this,
                    AgentService::class.java
                )

            stopService(intent)

            showStatus(
                "DISCONNECTED",
                false
            )

            Toast.makeText(
                this,
                "Android Agent stopped",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            showStatus(
                "ERROR • ${e.message}",
                false
            )
        }
    }

    private fun showStatus(
        message: String,
        success: Boolean?
    ) {

        statusText.text = message

        statusText.setTextColor(
            when (success) {
                true ->
                    Color.rgb(
                        80,
                        220,
                        140
                    )

                false ->
                    Color.rgb(
                        255,
                        100,
                        100
                    )

                null ->
                    Color.rgb(
                        255,
                        190,
                        80
                    )
            }
        )
    }

    private fun dp(value: Int): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
