package com.netwatch.agent

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var dashboard: EditText
    private lateinit var router: EditText
    private lateinit var key: EditText

    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusDot: TextView

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var testButton: Button

    private val prefs by lazy {
        getSharedPreferences("agent", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(2, 6, 23)
        window.navigationBarColor = Color.rgb(2, 6, 23)

        buildUi()
        restoreValues()
    }

    private fun buildUi() {

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(2, 6, 23))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 32)
        }

        // HEADER
        val title = TextView(this).apply {
            text = "NetWatch"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Android Network Agent"
            textSize = 15f
            setTextColor(Color.rgb(148, 163, 184))
            setPadding(0, 4, 0, 24)
        }

        root.addView(subtitle)

        // STATUS CARD
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = cardBackground()
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusDot = TextView(this).apply {
            text = "●"
            textSize = 25f
            setTextColor(Color.rgb(148, 163, 184))
        }

        val statusTextBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 0, 0)
        }

        statusTitle = TextView(this).apply {
            text = "Agent stopped"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        statusDetail = TextView(this).apply {
            text = "Not connected to dashboard"
            textSize = 13f
            setTextColor(Color.rgb(148, 163, 184))
            setPadding(0, 4, 0, 0)
        }

        statusTextBox.addView(statusTitle)
        statusTextBox.addView(statusDetail)

        statusRow.addView(statusDot)
        statusRow.addView(statusTextBox)

        statusCard.addView(statusRow)

        root.addView(
            statusCard,
            marginParams(0, 0, 0, 18)
        )

        // DASHBOARD CONNECTION
        root.addView(
            sectionTitle("Dashboard Connection")
        )

        dashboard = field("Dashboard URL")

        router = field(
            "Router IP",
            "192.168.100.1"
        )

        key = field("Agent Key")

        key.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD

        root.addView(dashboard)

        root.addView(
            router,
            marginParams(0, 10, 0, 0)
        )

        root.addView(
            key,
            marginParams(0, 10, 0, 0)
        )

        // TEST CONNECTION
        testButton = button(
            "Test Dashboard Connection",
            false
        )

        testButton.setOnClickListener {
            testConnection()
        }

        root.addView(
            testButton,
            marginParams(0, 16, 0, 0)
        )

        // AGENT CONTROLS
        root.addView(
            sectionTitle("Agent Controls"),
            marginParams(0, 26, 0, 0)
        )

        startButton = button(
            "Start Agent",
            true
        )

        startButton.setOnClickListener {
            startAgent()
        }

        root.addView(startButton)

        stopButton = button(
            "Stop Agent",
            false
        )

        stopButton.setOnClickListener {
            stopAgent()
        }

        root.addView(
            stopButton,
            marginParams(0, 10, 0, 0)
        )

        // INFO
        val info = TextView(this).apply {
            text =
                "The phone must be connected to the same Wi-Fi network you want to monitor. " +
                "The dashboard is reached through the internet; the LAN scan happens locally on this phone."

            textSize = 12f
            setTextColor(Color.rgb(100, 116, 139))
            setPadding(4, 18, 4, 0)
        }

        root.addView(info)

        scroll.addView(root)

        setContentView(scroll)
    }

    private fun restoreValues() {

        dashboard.setText(
            prefs.getString(
                "dashboard",
                "https://network-device-dashboard-tydeft.v2.appdeploy.ai"
            ) ?: ""
        )

        router.setText(
            prefs.getString(
                "router",
                "192.168.100.1"
            ) ?: "192.168.100.1"
        )

        key.setText(
            prefs.getString(
                "key",
                ""
            ) ?: ""
        )
    }

    private fun saveValues() {

        prefs.edit()
            .putString(
                "dashboard",
                dashboard.text.toString().trim()
            )
            .putString(
                "router",
                router.text.toString().trim()
            )
            .putString(
                "key",
                key.text.toString().trim()
            )
            .apply()
    }

    private fun startAgent() {

        saveValues()

        val intent = Intent(
            this,
            AgentService::class.java
        )

        startForegroundService(intent)

        setStatus(
            "Connecting...",
            "Testing dashboard registration",
            Color.rgb(250, 204, 21)
        )

        lifecycleScope.launch {
            testConnection()
        }
    }

    private fun stopAgent() {

        stopService(
            Intent(
                this,
                AgentService::class.java
            )
        )

        setStatus(
            "Agent stopped",
            "The background agent is not running",
            Color.rgb(148, 163, 184)
        )
    }

    private fun testConnection() {

        saveValues()

        testButton.isEnabled = false

        setStatus(
            "Checking...",
            "Connecting to /api/agent/register",
            Color.rgb(250, 204, 21)
        )

        lifecycleScope.launch {

            val result = withContext(Dispatchers.IO) {
                registerTest()
            }

            testButton.isEnabled = true

            if (result.first) {

                setStatus(
                    "Dashboard connected",
                    "Agent registration successful • ${result.second}",
                    Color.rgb(52, 211, 153)
                )

            } else {

                setStatus(
                    "Connection failed",
                    result.second,
                    Color.rgb(248, 113, 113)
                )
            }
        }
    }

    private fun registerTest(): Pair<Boolean, String> {

        val base =
            dashboard.text.toString()
                .trim()
                .trimEnd('/')

        val routerIp =
            router.text.toString()
                .trim()

        val agentKey =
            key.text.toString()
                .trim()

        if (base.isBlank()) {
            return false to "Dashboard URL is empty"
        }

        if (routerIp.isBlank()) {
            return false to "Router IP is empty"
        }

        if (agentKey.isBlank()) {
            return false to "Agent Key is empty"
        }

        var connection: HttpURLConnection? = null

        return try {

            val agentId =
                prefs.getString(
                    "agent_id",
                    null
                ) ?: run {

                    val id =
                        "android-" +
                        System.currentTimeMillis()

                    prefs.edit()
                        .putString(
                            "agent_id",
                            id
                        )
                        .apply()

                    id
                }

            val body = JSONObject().apply {

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
                    agentKey
                )

                put(
                    "platform",
                    "android"
                )

                put(
                    "version",
                    "1.0.1"
                )
            }

            connection =
                URL(
                    "$base/api/agent/register"
                ).openConnection()
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

            connection.outputStream.use {

                it.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            val code =
                connection.responseCode

            val stream =
                if (code in 200..299)
                    connection.inputStream
                else
                    connection.errorStream

            stream?.use {

                BufferedReader(
                    InputStreamReader(it)
                ).readText()
            }

            if (code in 200..299) {

                true to "HTTP $code"

            } else {

                val message =
                    when (code) {

                        401 ->
                            "Unauthorized"

                        403 ->
                            "Invalid Agent Key"

                        404 ->
                            "API endpoint not found. Check Dashboard URL."

                        500 ->
                            "Dashboard server error"

                        else ->
                            "HTTP $code"
                    }

                false to message
            }

        } catch (e: Exception) {

            false to (
                e.message
                    ?: "Unable to reach dashboard"
            )

        } finally {

            connection?.disconnect()
        }
    }

    private fun setStatus(
        title: String,
        detail: String,
        color: Int
    ) {

        statusTitle.text = title

        statusDetail.text = detail

        statusDot.setTextColor(color)
    }

    private fun sectionTitle(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text

            textSize = 13f

            setTextColor(
                Color.rgb(
                    148,
                    163,
                    184
                )
            )

            typeface =
                Typeface.DEFAULT_BOLD

            setPadding(
                2,
                0,
                0,
                10
            )
        }
    }

    private fun field(
        hintText: String,
        defaultValue: String = ""
    ): EditText {

        return EditText(this).apply {

            hint = hintText

            setText(defaultValue)

            textSize = 14f

            setTextColor(Color.WHITE)

            setHintTextColor(
                Color.rgb(
                    100,
                    116,
                    139
                )
            )

            setSingleLine(true)

            setPadding(
                16,
                14,
                16,
                14
            )

            background =
                roundedBackground(
                    Color.rgb(
                        15,
                        23,
                        42
                    ),
                    Color.rgb(
                        51,
                        65,
                        85
                    )
                )
        }
    }

    private fun button(
        textValue: String,
        primary: Boolean
    ): Button {

        return Button(this).apply {

            text = textValue

            textSize = 14f

            typeface =
                Typeface.DEFAULT_BOLD

            isAllCaps = false

            setTextColor(
                if (primary)
                    Color.rgb(
                        2,
                        6,
                        23
                    )
                else
                    Color.WHITE
            )

            background =
                roundedBackground(
                    if (primary)
                        Color.rgb(
                            52,
                            211,
                            153
                        )
                    else
                        Color.rgb(
                            15,
                            23,
                            42
                        ),

                    if (primary)
                        Color.rgb(
                            52,
                            211,
                            153
                        )
                    else
                        Color.rgb(
                            51,
                            65,
                            85
                        )
                )

            minimumHeight = 54
        }
    }

    private fun cardBackground():
        GradientDrawable {

        return roundedBackground(
            Color.rgb(
                15,
                23,
                42
            ),
            Color.rgb(
                30,
                41,
                59
            )
        )
    }

    private fun roundedBackground(
        fill: Int,
        stroke: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(fill)

            setStroke(
                1,
                stroke
            )

            cornerRadius = 22f
        }
    }

    private fun marginParams(
        l: Int,
        t: Int,
        r: Int,
        b: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {

            setMargins(
                l,
                t,
                r,
                b
            )
        }
    }
}
