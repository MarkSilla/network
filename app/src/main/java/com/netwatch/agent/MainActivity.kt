package com.netwatch.agent

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        private const val PREFS_NAME =
            "netwatch"

        private const val AGENT_ID_KEY =
            "agentId"

        private const val AGENT_KEY_KEY =
            "agentKey"
    }

    private lateinit var agentKeyText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var devicesText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var routerInput: EditText

    private lateinit var copyKeyButton: Button
    private lateinit var regenerateKeyButton: Button
    private lateinit var startButton: Button

    private var paired =
        false

    private var checkingPairing =
        false

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val pairingRunnable =
        object : Runnable {

            override fun run() {

                checkPairing()

                if (!paired) {

                    handler.postDelayed(
                        this,
                        3000
                    )
                }
            }
        }

    private val statusReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action !=
                    AgentService.ACTION_STATUS
                ) {
                    return
                }

                val status =
                    intent.getStringExtra(
                        AgentService.EXTRA_STATUS
                    )
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

                    statusText.text =
                        status

                    progressText.text =
                        if (
                            status.contains(
                                "Scanning",
                                ignoreCase = true
                            )
                        ) {

                            "Scanning $progress/$total"

                        } else {

                            status
                        }

                    devicesText.text =
                        "Devices found: $found"

                    progressBar.max =
                        total

                    progressBar.progress =
                        progress
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        val agentId =
            getAgentId()

        val agentKey =
            getAgentKey()

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
                    Color.rgb(
                        10,
                        15,
                        25
                    )
                )
            }

        val title =
            TextView(this).apply {

                text =
                    "NetWatch Agent"

                textSize =
                    26f

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    0,
                    0,
                    20
                )
            }

        layout.addView(
            title
        )

        val subtitle =
            TextView(this).apply {

                text =
                    "This Android device has its own Agent Key. " +
                    "Open the NetWatch Dashboard on your browser " +
                    "and pair this Android agent using the key below."

                textSize =
                    16f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    25
                )
            }

        layout.addView(
            subtitle
        )

        /*
         * AGENT KEY LABEL
         */

        val keyLabel =
            TextView(this).apply {

                text =
                    "YOUR AGENT KEY"

                textSize =
                    13f

                setTextColor(
                    Color.rgb(
                        120,
                        180,
                        255
                    )
                )

                setPadding(
                    0,
                    0,
                    0,
                    8
                )
            }

        layout.addView(
            keyLabel
        )

        /*
         * AGENT KEY
         */

        agentKeyText =
            TextView(this).apply {

                text =
                    agentKey

                textSize =
                    16f

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    20,
                    20,
                    20,
                    20
                )

                setBackgroundColor(
                    Color.rgb(
                        25,
                        35,
                        50
                    )
                )

                setTextIsSelectable(
                    true
                )
            }

        layout.addView(
            agentKeyText
        )

        /*
         * COPY BUTTON
         */

        copyKeyButton =
            Button(this).apply {

                text =
                    "COPY AGENT KEY"

                setOnClickListener {

                    copyAgentKey()
                }
            }

        layout.addView(
            copyKeyButton
        )

        /*
         * GENERATE NEW KEY
         */

        regenerateKeyButton =
            Button(this).apply {

                text =
                    "GENERATE NEW KEY"

                setOnClickListener {

                    regenerateAgentKey()
                }
            }

        layout.addView(
            regenerateKeyButton
        )

        /*
         * AGENT ID
         */

        val agentIdText =
            TextView(this).apply {

                text =
                    "Agent ID: $agentId"

                textSize =
                    12f

                setTextColor(
                    Color.GRAY
                )

                setPadding(
                    0,
                    10,
                    0,
                    10
                )
            }

        layout.addView(
            agentIdText
        )

        /*
         * STATUS
         */

        statusText =
            TextView(this).apply {

                text =
                    "🟡 WAITING FOR BROWSER PAIRING"

                textSize =
                    18f

                setTextColor(
                    Color.WHITE
                )

                setPadding(
                    0,
                    25,
                    0,
                    10
                )
            }

        layout.addView(
            statusText
        )

        /*
         * PROGRESS TEXT
         */

        progressText =
            TextView(this).apply {

                text =
                    "Connecting to the NetWatch Dashboard..."

                textSize =
                    15f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    20
                )
            }

        layout.addView(
            progressText
        )

        /*
         * ROUTER IP
         *
         * Hidden until paired.
         */

        routerInput =
            createInput(
                "Router IP",
                "192.168.100.1"
            )

        routerInput.visibility =
            View.GONE

        layout.addView(
            routerInput
        )

        /*
         * START AGENT
         */

        startButton =
            Button(this).apply {

                text =
                    "START AGENT"

                visibility =
                    View.GONE

                setOnClickListener {

                    startAgent()
                }
            }

        layout.addView(
            startButton
        )

        /*
         * PROGRESS BAR
         */

        progressBar =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {

                max =
                    254

                progress =
                    0
            }

        layout.addView(
            progressBar
        )

        /*
         * DEVICES
         */

        devicesText =
            TextView(this).apply {

                text =
                    "Devices found: 0"

                textSize =
                    15f

                setTextColor(
                    Color.LTGRAY
                )

                setPadding(
                    0,
                    12,
                    0,
                    20
                )
            }

        layout.addView(
            devicesText
        )

        /*
         * STOP
         */

        val stopButton =
            Button(this).apply {

                text =
                    "STOP AGENT"

                setOnClickListener {

                    stopAgent()
                }
            }

        layout.addView(
            stopButton
        )

        setContentView(
            layout
        )

        /*
         * STATUS RECEIVER
         */

        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(
                AgentService.ACTION_STATUS
            ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        /*
         * REGISTER + HEARTBEAT + PAIRING
         *
         * pairing-status automatically registers
         * the Android agent when it does not exist.
         */

        announceAgent()
    }

    /*
     * COPY AGENT KEY
     */

    private fun copyAgentKey() {

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "NetWatch Agent Key",
                getAgentKey()
            )
        )

        progressText.text =
            "Agent Key copied. Paste it into the browser dashboard."
    }

    /*
     * GENERATE NEW AGENT KEY
     */

    private fun regenerateAgentKey() {

        if (checkingPairing) {

            progressText.text =
                "Please wait while the agent is connecting..."

            return
        }

        /*
         * Stop current scan if running.
         */

        stopService(
            Intent(
                this,
                AgentService::class.java
            )
        )

        /*
         * Reset local pairing state.
         */

        paired =
            false

        checkingPairing =
            false

        handler.removeCallbacks(
            pairingRunnable
        )

        /*
         * Create completely new key.
         */

        val newKey =
            "NW-" +
            UUID.randomUUID()
                .toString()
                .uppercase()

        /*
         * Save new key.
         */

        getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                AGENT_KEY_KEY,
                newKey
            )
            .apply()

        /*
         * Update UI immediately.
         */

        agentKeyText.text =
            newKey

        routerInput.visibility =
            View.GONE

        startButton.visibility =
            View.GONE

        statusText.text =
            "🟡 NEW AGENT KEY GENERATED"

        progressText.text =
            "Connecting the new Agent Key..."

        /*
         * pairing-status will automatically
         * register the new key.
         */

        announceAgent()
    }

    /*
     * CREATE INPUT
     */

    private fun createInput(
        hint: String,
        value: String
    ): EditText {

        return EditText(
            this
        ).apply {

            this.hint =
                hint

            setText(
                value
            )

            setTextColor(
                Color.WHITE
            )

            setHintTextColor(
                Color.GRAY
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
     * REGISTER + HEARTBEAT
     *
     * Uses pairing-status as the Android
     * agent registration and heartbeat endpoint.
     *
     * The server automatically registers
     * the agent when the Agent ID + Agent Key
     * are unknown.
     */

    private fun announceAgent() {

        val agentId =
            getAgentId()

        val agentKey =
            getAgentKey()

        val hostname =
            android.os.Build.MODEL

        statusText.text =
            "🟡 CONNECTING TO DASHBOARD"

        progressText.text =
            "Connecting this Android agent..."

        thread {

            var connected =
                false

            for (attempt in 1..5) {

                if (paired) {
                    return@thread
                }

                try {

                    val statusUrl =
                        "$DASHBOARD_URL/api/agent/pairing-status" +
                        "?agentId=${encode(agentId)}" +
                        "&agentKey=${encode(agentKey)}" +
                        "&hostname=${encode(hostname)}"

                    val result =
                        getRequest(
                            statusUrl
                        )

                    val response =
                        result.second

                    val registered =
                        result.first in 200..299 &&
                        "\"registered\"\\s*:\\s*true"
                            .toRegex()
                            .containsMatchIn(
                                response
                            )

                    val isPaired =
                        result.first in 200..299 &&
                        "\"paired\"\\s*:\\s*true"
                            .toRegex()
                            .containsMatchIn(
                                response
                            )

                    if (registered) {

                        connected =
                            true

                        runOnUiThread {

                            if (isPaired) {

                                paired =
                                    true

                                statusText.text =
                                    "🟢 PAIRED"

                                progressText.text =
                                    "Browser approved this Android agent. Enter Router IP to scan."

                                routerInput.visibility =
                                    View.VISIBLE

                                startButton.visibility =
                                    View.VISIBLE

                                handler.removeCallbacks(
                                    pairingRunnable
                                )

                            } else {

                                statusText.text =
                                    "🟡 WAITING FOR BROWSER PAIRING"

                                progressText.text =
                                    "Agent registered. Paste this Agent Key into the browser dashboard."

                                /*
                                 * Start pairing checks only
                                 * after successful registration.
                                 */

                                handler.removeCallbacks(
                                    pairingRunnable
                                )

                                handler.post(
                                    pairingRunnable
                                )
                            }
                        }

                        break
                    }

                    runOnUiThread {

                        statusText.text =
                            "🟡 RETRYING CONNECTION"

                        progressText.text =
                            "Connection attempt $attempt/5 failed. Retrying..."
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        statusText.text =
                            "🟡 RETRYING CONNECTION"

                        progressText.text =
                            "Connection attempt $attempt/5 failed. Retrying..."
                    }
                }

                Thread.sleep(
                    3000
                )
            }

            if (
                !connected &&
                !paired
            ) {

                runOnUiThread {

                    statusText.text =
                        "🔴 DASHBOARD CONNECTION FAILED"

                    progressText.text =
                        "Could not connect to the dashboard. Check your internet connection and try again."
                }
            }
        }
    }

    /*
     * CHECK PAIRING
     *
     * Android asks the dashboard
     * every three seconds if the
     * browser has approved the agent.
     *
     * This also acts as a heartbeat.
     */

    private fun checkPairing() {

        if (
            checkingPairing ||
            paired
        ) {
            return
        }

        checkingPairing =
            true

        val agentId =
            getAgentId()

        val agentKey =
            getAgentKey()

        val hostname =
            android.os.Build.MODEL

        thread {

            try {

                val statusUrl =
                    "$DASHBOARD_URL/api/agent/pairing-status" +
                    "?agentId=${encode(agentId)}" +
                    "&agentKey=${encode(agentKey)}" +
                    "&hostname=${encode(hostname)}"

                val result =
                    getRequest(
                        statusUrl
                    )

                val response =
                    result.second

                val registered =
                    result.first in 200..299 &&
                    "\"registered\"\\s*:\\s*true"
                        .toRegex()
                        .containsMatchIn(
                            response
                        )

                val isPaired =
                    result.first in 200..299 &&
                    "\"paired\"\\s*:\\s*true"
                        .toRegex()
                        .containsMatchIn(
                            response
                        )

                runOnUiThread {

                    when {

                        isPaired -> {

                            paired =
                                true

                            statusText.text =
                                "🟢 PAIRED"

                            progressText.text =
                                "Browser approved this Android agent. Enter Router IP to scan."

                            routerInput.visibility =
                                View.VISIBLE

                            startButton.visibility =
                                View.VISIBLE

                            handler.removeCallbacks(
                                pairingRunnable
                            )
                        }

                        registered -> {

                            statusText.text =
                                "🟡 WAITING FOR BROWSER PAIRING"

                            progressText.text =
                                "Waiting for the browser to approve this Agent Key..."
                        }

                        else -> {

                            statusText.text =
                                "🟡 REGISTERING AGENT"

                            progressText.text =
                                "Registering this Android agent with the dashboard..."
                        }
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "🟡 WAITING FOR CONNECTION"

                    progressText.text =
                        "Checking dashboard..."
                }

            } finally {

                checkingPairing =
                    false
            }
        }
    }

    /*
     * START AGENT
     */

    private fun startAgent() {

        if (!paired) {
            return
        }

        val router =
            routerInput.text
                .toString()
                .trim()

        if (router.isBlank()) {

            progressText.text =
                "Enter the Router IP first"

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
                    router
                )

                putExtra(
                    "agentKey",
                    getAgentKey()
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
            "🟢 AGENT RUNNING"

        progressText.text =
            "Starting local network scan..."

        progressBar.progress =
            0

        devicesText.text =
            "Devices found: 0"
    }

    /*
     * STOP AGENT
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
                "🟢 PAIRED • OFFLINE"

            progressText.text =
                "Agent stopped. Pairing is still active."

        } else {

            statusText.text =
                "🟡 WAITING FOR BROWSER PAIRING"

            progressText.text =
                "Waiting for browser approval."
        }

        progressBar.progress =
            0

        devicesText.text =
            "Devices found: 0"
    }

    /*
     * GET PERSISTENT AGENT ID
     */

    private fun getAgentId(): String {

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        var id =
            prefs.getString(
                AGENT_ID_KEY,
                null
            )

        if (id.isNullOrBlank()) {

            id =
                "android-${UUID.randomUUID()}"

            prefs.edit()
                .putString(
                    AGENT_ID_KEY,
                    id
                )
                .apply()
        }

        return id
    }

    /*
     * GET PERSISTENT AGENT KEY
     *
     * The Android app generates
     * and stores the key.
     */

    private fun getAgentKey(): String {

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        var key =
            prefs.getString(
                AGENT_KEY_KEY,
                null
            )

        if (key.isNullOrBlank()) {

            key =
                "NW-" +
                UUID.randomUUID()
                    .toString()
                    .uppercase()

            prefs.edit()
                .putString(
                    AGENT_KEY_KEY,
                    key
                )
                .apply()
        }

        return key
    }

    /*
     * URL ENCODING
     */

    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }

    /*
     * JSON ESCAPING
     *
     * Kept for compatibility with
     * other code that may use it.
     */

    private fun jsonEscape(
        value: String
    ): String {

        return value
            .replace(
                "\\",
                "\\\\"
            )
            .replace(
                "\"",
                "\\\""
            )
            .replace(
                "\n",
                "\\n"
            )
            .replace(
                "\r",
                "\\r"
            )
            .replace(
                "\t",
                "\\t"
            )
    }

    /*
     * POST REQUEST
     *
     * Kept because other future functionality
     * may require POST requests.
     */

    private fun postRequest(
        urlString: String,
        body: String
    ): Pair<Int, String> {

        var connection:
            HttpURLConnection? =
            null

        return try {

            connection =
                URL(
                    urlString
                )
                    .openConnection()
                        as HttpURLConnection

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                8000

            connection.readTimeout =
                8000

            connection.doOutput =
                true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.outputStream
                .use { output ->

                    output.write(
                        body.toByteArray(
                            Charsets.UTF_8
                        )
                    )
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

            val responseBody =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    ?: ""

            Pair(
                responseCode,
                responseBody
            )

        } finally {

            connection?.disconnect()
        }
    }

    /*
     * GET REQUEST
     */

    private fun getRequest(
        urlString: String
    ): Pair<Int, String> {

        var connection:
            HttpURLConnection? =
            null

        return try {

            connection =
                URL(
                    urlString
                )
                    .openConnection()
                        as HttpURLConnection

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

            val responseBody =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    ?: ""

            Pair(
                responseCode,
                responseBody
            )

        } finally {

            connection?.disconnect()
        }
    }

    /*
     * CLEAN UP
     */

    override fun onDestroy() {

        handler.removeCallbacks(
            pairingRunnable
        )

        try {

            unregisterReceiver(
                statusReceiver
            )

        } catch (
            _: Exception
        ) {
        }

        super.onDestroy()
    }
}
