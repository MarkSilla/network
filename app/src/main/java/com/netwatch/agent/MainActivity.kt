package com.netwatch.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var devicesText: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var dashboardInput: EditText
    private lateinit var routerInput: EditText
    private lateinit var keyInput: EditText

    private val statusReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {

            if (intent?.action != AgentService.ACTION_STATUS) {
                return
            }

            val status =
                intent.getStringExtra(AgentService.EXTRA_STATUS)
                    ?: "Agent Running"

            val progress =
                intent.getIntExtra(
                    AgentService.EXTRA_PROGRESS,
                    0
                )

            val total =
                intent.getIntExtra(
                    AgentService.EXTRA_TOTAL,
                    254
                )

            val found =
                intent.getIntExtra(
                    AgentService.EXTRA_FOUND,
                    0
                )

            runOnUiThread {

                statusText.text = status

                progressText.text =
                    if (status.contains("Scanning")) {
                        "Scanning $progress/$total"
                    } else {
                        status
                    }

                devicesText.text =
                    "Devices found: $found"

                progressBar.max = total
                progressBar.progress = progress
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 40, 40, 40)

        layout.setBackgroundColor(
            android.graphics.Color.rgb(10, 15, 25)
        )

        dashboardInput =
            createInput(
                "Dashboard URL",
                "https://network-device-dashboard-tydeft.v2.appdeploy.ai"
            )

        routerInput =
            createInput(
                "Router IP",
                "192.168.100.1"
            )

        keyInput =
            createInput(
                "Agent Key",
                ""
            )

        layout.addView(dashboardInput)
        layout.addView(routerInput)
        layout.addView(keyInput)

        statusText = TextView(this)

        statusText.text =
            "Dashboard connection not tested"

        statusText.textSize = 17f
        statusText.setTextColor(
            android.graphics.Color.WHITE
        )

        statusText.setPadding(0, 30, 0, 15)

        layout.addView(statusText)

        progressText = TextView(this)

        progressText.text =
            "Ready"

        progressText.textSize = 16f

        progressText.setTextColor(
            android.graphics.Color.LTGRAY
        )

        layout.addView(progressText)

        progressBar =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            )

        progressBar.max = 254
        progressBar.progress = 0

        layout.addView(progressBar)

        devicesText = TextView(this)

        devicesText.text =
            "Devices found: 0"

        devicesText.textSize = 15f

        devicesText.setTextColor(
            android.graphics.Color.LTGRAY
        )

        devicesText.setPadding(0, 12, 0, 20)

        layout.addView(devicesText)

        val testButton =
            Button(this)

        testButton.text =
            "TEST DASHBOARD CONNECTION"

        testButton.setOnClickListener {
            testDashboardConnection()
        }

        layout.addView(testButton)

        val startButton =
            Button(this)

        startButton.text =
            "START AGENT"

        startButton.setOnClickListener {
            startAgent()
        }

        layout.addView(startButton)

        val stopButton =
            Button(this)

        stopButton.text =
            "STOP AGENT"

        stopButton.setOnClickListener {
            stopAgent()
        }

        layout.addView(stopButton)

        setContentView(layout)

        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(AgentService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun createInput(
        hint: String,
        value: String
    ): EditText {

        val input = EditText(this)

        input.hint = hint
        input.setText(value)

        input.setTextColor(
            android.graphics.Color.WHITE
        )

        input.setHintTextColor(
            android.graphics.Color.GRAY
        )

        input.setPadding(20, 15, 20, 15)

        return input
    }

    private fun testDashboardConnection() {

        statusText.text =
            "Testing dashboard connection..."

        thread {

            try {

                val dashboard =
                    dashboardInput.text
                        .toString()
                        .trim()
                        .trimEnd('/')

                val router =
                    routerInput.text
                        .toString()
                        .trim()

                val key =
                    keyInput.text
                        .toString()
                        .trim()

                val agentId = getAgentId()

                val url =
                    "$dashboard/api/agent/register" +
                            "?agentId=${encode(agentId)}" +
                            "&routerIp=${encode(router)}" +
                            "&agentKey=${encode(key)}" +
                            "&hostname=${encode(android.os.Build.MODEL)}"

                val result =
                    getRequest(url)

                runOnUiThread {

                    if (result.first in 200..299) {

                        statusText.text =
                            "CONNECTED"

                        progressText.text =
                            "Dashboard connection successful"

                    } else {

                        statusText.text =
                            "CONNECTION FAILED (${result.first})"

                        progressText.text =
                            result.second
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "CONNECTION FAILED"

                    progressText.text =
                        e.message ?: "Unknown error"
                }
            }
        }
    }

    private fun startAgent() {

        val intent =
            Intent(this, AgentService::class.java)

        intent.putExtra(
            "dashboardUrl",
            dashboardInput.text.toString().trim()
        )

        intent.putExtra(
            "routerIp",
            routerInput.text.toString().trim()
        )

        intent.putExtra(
            "agentKey",
            keyInput.text.toString().trim()
        )

        intent.putExtra(
            "agentId",
            getAgentId()
        )

        ContextCompat.startForegroundService(
            this,
            intent
        )

        statusText.text =
            "Agent Running"

        progressText.text =
            "Starting local network scan..."

        progressBar.max = 254
        progressBar.progress = 0

        devicesText.text =
            "Devices found: 0"
    }

    private fun stopAgent() {

        stopService(
            Intent(this, AgentService::class.java)
        )

        statusText.text =
            "Agent Stopped"

        progressText.text =
            "Ready"

        progressBar.progress = 0

        devicesText.text =
            "Devices found: 0"
    }

    private fun getAgentId(): String {

        val prefs =
            getSharedPreferences(
                "netwatch",
                Context.MODE_PRIVATE
            )

        var id =
            prefs.getString(
                "agentId",
                null
            )

        if (id == null) {

            id =
                "android-${UUID.randomUUID()}"

            prefs.edit()
                .putString("agentId", id)
                .apply()
        }

        return id
    }

    private fun encode(value: String): String {

        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }

    private fun getRequest(
        urlString: String
    ): Pair<Int, String> {

        val connection =
            URL(urlString)
                .openConnection() as HttpURLConnection

        return try {

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                8000

            connection.readTimeout =
                8000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val status =
                connection.responseCode

            val stream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body =
                stream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""

            Pair(status, body)

        } finally {

            connection.disconnect()
        }
    }

    override fun onDestroy() {

        try {
            unregisterReceiver(statusReceiver)
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
