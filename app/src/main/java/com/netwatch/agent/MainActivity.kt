package com.netwatch.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
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

    companion object {
        private const val DASHBOARD_URL =
            "https://network-device-dashboard-tydeft.v2.appdeploy.ai"
    }

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var devicesText: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var keyInput: EditText
    private lateinit var routerInput: EditText

    private lateinit var pairButton: Button
    private lateinit var startButton: Button

    private var paired = false

    private val statusReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {

            if (intent?.action != AgentService.ACTION_STATUS) {
                return
            }

            val status =
                intent.getStringExtra(
                    AgentService.EXTRA_STATUS
                ) ?: "Agent Running"

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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    40,
                    40,
                    40,
                    40
                )

                setBackgroundColor(
                    android.graphics.Color.rgb(
                        10,
                        15,
                        25
                    )
                )
            }

        val title =
            TextView(this).apply {

                text = "NetWatch Agent"

                textSize = 26f

                setTextColor(
                    android.graphics.Color.WHITE
                )

                setPadding(
                    0,
                    0,
                    0,
                    20
                )
            }

        layout.addView(title)

        val subtitle =
            TextView(this).apply {

                text =
                    "Enter your Agent Key to pair with the dashboard."

                textSize = 16f

                setTextColor(
                    android.graphics.Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    20
                )
            }

        layout.addView(subtitle)

        /*
         * AGENT KEY
         */

        keyInput =
            createInput(
                "Agent Key",
                ""
            )

        layout.addView(keyInput)

        /*
         * PAIR BUTTON
         */

        pairButton =
            Button(this).apply {

                text = "PAIR AGENT"

                setOnClickListener {
                    pairAgent()
                }
            }

        layout.addView(pairButton)

        /*
         * STATUS
         */

        statusText =
            TextView(this).apply {

                text = "🔴 NOT PAIRED"

                textSize = 18f

                setTextColor(
                    android.graphics.Color.WHITE
                )

                setPadding(
                    0,
                    25,
                    0,
                    10
                )
            }

        layout.addView(statusText)

        progressText =
            TextView(this).apply {

                text =
                    "Enter Agent Key to begin pairing"

                textSize = 15f

                setTextColor(
                    android.graphics.Color.LTGRAY
                )
            }

        layout.addView(progressText)

        /*
         * ROUTER IP
         *
         * Hidden until pairing succeeds.
         */

        routerInput =
            createInput(
                "Router IP",
                "192.168.100.1"
            )

        routerInput.visibility =
            View.GONE

        layout.addView(routerInput)

        /*
         * START AGENT
         *
         * Hidden until pairing succeeds.
         */

        startButton =
            Button(this).apply {

                text = "START AGENT"

                visibility =
                    View.GONE

                setOnClickListener {
                    startAgent()
                }
            }

        layout.addView(startButton)

        /*
         * PROGRESS
         */

        progressBar =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {

                max = 254

                progress = 0
            }

        layout.addView(progressBar)

        /*
         * DEVICES
         */

        devicesText =
            TextView(this).apply {

                text =
                    "Devices found: 0"

                textSize = 15f

                setTextColor(
                    android.graphics.Color.LTGRAY
                )

                setPadding(
                    0,
                    12,
                    0,
                    20
                )
            }

        layout.addView(devicesText)

        /*
         * STOP
         */

        val stopButton =
            Button(this).apply {

                text = "STOP AGENT"

                setOnClickListener {
                    stopAgent()
                }
            }

        layout.addView(stopButton)

        setContentView(layout)

        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(
                AgentService.ACTION_STATUS
            ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun createInput(
        hint: String,
        value: String
    ): EditText {

        return EditText(this).apply {

            this.hint = hint

            setText(value)

            setTextColor(
                android.graphics.Color.WHITE
            )

            setHintTextColor(
                android.graphics.Color.GRAY
            )

            setPadding(
                20,
                15,
                20,
                15
            )
        }
    }

    /*
     * PAIRING
     */

    private fun pairAgent() {

        val key =
            keyInput.text
                .toString()
                .trim()

        if (key.isBlank()) {

            statusText.text =
                "🔴 NOT PAIRED"

            progressText.text =
                "Enter an Agent Key first"

            return
        }

        pairButton.isEnabled =
            false

        keyInput.isEnabled =
            false

        statusText.text =
            "🟡 PAIRING..."

        progressText.text =
            "Waiting for dashboard connection..."

        thread {

            try {

                val router =
                    routerInput.text
                        .toString()
                        .trim()

                /*
                 * First register this Android agent
                 * using the supplied Agent Key.
                 */

                val registerUrl =
                    "$DASHBOARD_URL/api/agent/register" +
                            "?agentId=${encode(getAgentId())}" +
                            "&routerIp=${encode(router)}" +
                            "&agentKey=${encode(key)}" +
                            "&hostname=${encode(android.os.Build.MODEL)}"

                val registerResult =
                    getRequest(registerUrl)

                if (registerResult.first !in 200..299) {

                    runOnUiThread {

                        statusText.text =
                            "🔴 NOT PAIRED"

                        progressText.text =
                            "Dashboard rejected pairing (${registerResult.first})"

                        pairButton.isEnabled =
                            true

                        keyInput.isEnabled =
                            true
                    }

                    return@thread
                }

                /*
                 * Registration succeeded.
                 *
                 * Now verify that the dashboard
                 * recognizes this Agent Key.
                 */

                val statusUrl =
                    "$DASHBOARD_URL/api/agent/status" +
                            "?agentKey=${encode(key)}"

                val statusResult =
                    getRequest(statusUrl)

                if (statusResult.first !in 200..299) {

                    runOnUiThread {

                        statusText.text =
                            "🟡 WAITING FOR CONNECTION"

                        progressText.text =
                            "Waiting for dashboard pairing..."

                        pairButton.isEnabled =
                            true

                        keyInput.isEnabled =
                            true
                    }

                    return@thread
                }

                /*
                 * Dashboard accepted the Agent Key.
                 */

                runOnUiThread {

                    paired =
                        true

                    statusText.text =
                        "🟢 PAIRED / CONNECTED"

                    progressText.text =
                        "Agent Key matched. Ready to scan."

                    routerInput.visibility =
                        View.VISIBLE

                    startButton.visibility =
                        View.VISIBLE

                    pairButton.text =
                        "PAIRED"
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "🟡 WAITING FOR CONNECTION"

                    progressText.text =
                        e.message
                            ?: "Dashboard connection failed"

                    pairButton.isEnabled =
                        true

                    keyInput.isEnabled =
                        true
                }
            }
        }
    }

    /*
     * START SCANNING
     */

    private fun startAgent() {

        if (!paired) {
            return
        }

        val intent =
            Intent(
                this,
                AgentService::class.java
            ).apply {

                putExtra(
                    "dashboardUrl",
                    DASHBOARD_URL
                )

                putExtra(
                    "routerIp",
                    routerInput.text
                        .toString()
                        .trim()
                )

                putExtra(
                    "agentKey",
                    keyInput.text
                        .toString()
                        .trim()
                )

                putExtra(
                    "agentId",
                    getAgentId()
                )
            }

        ContextCompat.startForegroundService(
            this,
            intent
        )

        statusText.text =
            "🟢 PAIRED / CONNECTED"

        progressText.text =
            "Starting local network scan..."

        progressBar.progress =
            0

        devicesText.text =
            "Devices found: 0"
    }

    /*
     * STOP
     */

    private fun stopAgent() {

        stopService(
            Intent(
                this,
                AgentService::class.java
            )
        )

        if (paired) {

            statusText.text =
                "🟡 PAIRED • OFFLINE"

            progressText.text =
                "Agent stopped"

        } else {

            statusText.text =
                "🔴 NOT PAIRED"

            progressText.text =
                "Enter Agent Key to begin pairing"
        }

        progressBar.progress =
            0

        devicesText.text =
            "Devices found: 0"
    }

    /*
     * PERSISTENT AGENT ID
     */

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
                .putString(
                    "agentId",
                    id
                )
                .apply()
        }

        return id
    }

    private fun encode(
        value: String
    ): String {

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
                .openConnection()
                    as HttpURLConnection

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
                    ?.use {
                        it.readText()
                    }
                    ?: ""

            Pair(
                status,
                body
            )

        } finally {

            connection.disconnect()
        }
    }

    override fun onDestroy() {

        try {
            unregisterReceiver(
                statusReceiver
            )
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
