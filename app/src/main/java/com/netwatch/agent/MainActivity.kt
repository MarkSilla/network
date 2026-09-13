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
import org.json.JSONObject
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

    private var paired = false
    private var checkingPairing = false

    private val handler =
        Handler(Looper.getMainLooper())

    private val pairingRunnable =
        object : Runnable {

            override fun run() {

                if (!paired) {
                    checkPairing()
                }

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

        layout.addView(title)

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

        layout.addView(subtitle)

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

        layout.addView(keyLabel)

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

        layout.addView(agentKeyText)

        copyKeyButton =
            Button(this).apply {

                text =
                    "COPY AGENT KEY"

                setOnClickListener {
                    copyAgentKey()
                }
            }

        layout.addView(copyKeyButton)

        regenerateKeyButton =
            Button(this).apply {

                text =
                    "GENERATE NEW KEY"

                setOnClickListener {
                    regenerateAgentKey()
                }
            }

        layout.addView(regenerateKeyButton)

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

        layout.addView(agentIdText)

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

        layout.addView(statusText)

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

        layout.addView(progressText)

        routerInput =
            createInput(
                "Router IP",
                "192.168.100.1"
            )

        routerInput.visibility =
            View.GONE

        layout.addView(routerInput)

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

        layout.addView(startButton)

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

        layout.addView(progressBar)

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

        layout.addView(devicesText)

        val stopButton =
            Button(this).apply {

                text =
                    "STOP AGENT"

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

        announceAgent()
    }

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

    private fun regenerateAgentKey() {

        if (checkingPairing) {

            progressText.text =
                "Please wait while the agent is connecting..."

            return
        }

        stopService(
            Intent(
                this,
                AgentService::class.java
            )
        )

        paired =
            false

        checkingPairing =
            false

        handler.removeCallbacks(
            pairingRunnable
        )

        val newKey =
            "NW-" +
            UUID.randomUUID()
                .toString()
                .uppercase()

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

        announceAgent()
    }

    private fun createInput(
        hint: String,
        value: String
    ): EditText {

        return EditText(this).apply {

            this.hint =
                hint

            setText(value)

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

    /**
     * INITIAL CONNECTION
     *
     * This is the important fix.
     *
     * The old version used regex to decide whether
     * the server registered the agent.
     *
     * This version uses JSONObject and reads:
     *
     * registered
     * paired
     * agentId
     * status
     *
     * It also displays the actual HTTP/connection
     * error instead of hiding it.
     */
    private fun announceAgent() {

        if (isFinishing || isDestroyed) {
            return
        }

        if (checkingPairing) {
            return
        }

        val agentId =
            getAgentId()

        val agentKey =
            getAgentKey()

        val hostname =
            android.os.Build.MODEL

        checkingPairing =
            true

        runOnUiThread {

            statusText.text =
                "🟡 CONNECTING TO DASHBOARD"

            progressText.text =
                "Connecting this Android agent..."
        }

        thread {

            var connected =
                false

            var lastError =
                "Unknown connection error"

            for (attempt in 1..5) {

                if (paired) {
                    break
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

                    val httpCode =
                        result.first

                    val responseBody =
                        result.second

                    if (
                        httpCode in 200..299
                    ) {

                        try {

                            val json =
                                JSONObject(
                                    responseBody
                                )

                            val registered =
                                json.optBoolean(
                                    "registered",
                                    false
                                )

                            val serverPaired =
                                json.optBoolean(
                                    "paired",
                                    false
                                )

                            if (registered) {

                                connected =
                                    true

                                lastError =
                                    ""

                                runOnUiThread {

                                    if (
                                        serverPaired
                                    ) {

                                        paired =
                                            true

                                        statusText.text =
                                            "🟢 PAIRED"

                                        progressText.text =
                                            "Browser approved this Android agent."

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

                                        routerInput.visibility =
                                            View.GONE

                                        startButton.visibility =
                                            View.GONE

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

                            lastError =
                                "Server connected, but registered=false."

                        } catch (
                            jsonError: Exception
                        ) {

                            lastError =
                                "Invalid server response: " +
                                (
                                    jsonError.message
                                        ?: "Invalid JSON"
                                )
                        }

                    } else {

                        lastError =
                            "HTTP $httpCode"

                        if (
                            responseBody.isNotBlank()
                        ) {

                            lastError +=
                                ": " +
                                responseBody
                                    .replace(
                                        "\n",
                                        " "
                                    )
                                    .take(180)
                        }
                    }

                } catch (
                    e: Exception
                ) {

                    lastError =
                        e.javaClass.simpleName +
                        ": " +
                        (
                            e.message
                                ?: "No details"
                        )
                }

                if (!connected) {

                    val errorForUi =
                        lastError

                    runOnUiThread {

                        statusText.text =
                            "🟡 RETRYING CONNECTION"

                        progressText.text =
                            "Attempt $attempt/5\n$errorForUi"
                    }

                    Thread.sleep(
                        1500
                    )
                }
            }

            checkingPairing =
                false

            if (
                !connected &&
                !paired
            ) {

                runOnUiThread {

                    statusText.text =
                        "🔴 DASHBOARD CONNECTION FAILED"

                    progressText.text =
                        lastError +
                        "\n\nThe app will keep checking for the dashboard."

                    routerInput.visibility =
                        View.GONE

                    startButton.visibility =
                        View.GONE
                }

                startPairingPolling()
            }
        }
    }

    /**
     * PAIRING CHECK
     *
     * Once the browser approves the Agent Key,
     * this detects paired=true.
     */
    private fun checkPairing() {

        if (
            paired ||
            checkingPairing
        ) {
            return
        }

        val agentId =
            getAgentId()

        val agentKey =
            getAgentKey()

        val hostname =
            android.os.Build.MODEL

        thread {

            try {

                val url =
                    "$DASHBOARD_URL/api/agent/pairing-status" +
                    "?agentId=${encode(agentId)}" +
                    "&agentKey=${encode(agentKey)}" +
                    "&hostname=${encode(hostname)}"

                val result =
                    getRequest(url)

                val httpCode =
                    result.first

                val body =
                    result.second

                if (
                    httpCode in 200..299
                ) {

                    val json =
                        JSONObject(body)

                    val registered =
                        json.optBoolean(
                            "registered",
                            false
                        )

                    val serverPaired =
                        json.optBoolean(
                            "paired",
                            false
                        )

                    if (registered) {

                        runOnUiThread {

                            if (
                                serverPaired
                            ) {

                                paired =
                                    true

                                statusText.text =
                                    "🟢 PAIRED"

                                progressText.text =
                                    "Browser approved this Android agent."

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
                                    "Paste the Agent Key into the browser dashboard."
                            }
                        }
                    }
                }

            } catch (
                e: Exception
            ) {

                runOnUiThread {

                    if (!paired) {

                        statusText.text =
                            "🟡 WAITING FOR BROWSER PAIRING"

                        progressText.text =
                            "Waiting for browser pairing..."
                    }
                }
            }
        }
    }

    private fun startPairingPolling() {

        handler.removeCallbacks(
            pairingRunnable
        )

        if (!paired) {

            handler.post(
                pairingRunnable
            )
        }
    }

    /**
     * START AGENT SERVICE
     */
    private fun startAgent() {

        if (!paired) {

            progressText.text =
                "Pair this Android agent in the browser first."

            return
        }

        val routerIp =
            routerInput.text
                .toString()
                .trim()

        if (routerIp.isBlank()) {

            routerInput.error =
                "Enter Router IP"

            return
        }

        val intent =
            Intent(
                this,
                AgentService::class.java
            ).apply {

                putExtra(
                    "agentId",
                    getAgentId()
                )

                putExtra(
                    "agentKey",
                    getAgentKey()
                )

                putExtra(
                    "routerIp",
                    routerIp
                )

                putExtra(
                    "dashboardUrl",
                    DASHBOARD_URL
                )
            }

        ContextCompat.startForegroundService(
            this,
            intent
        )

        statusText.text =
            "🟢 AGENT RUNNING"

        progressText.text =
            "Scanning router $routerIp..."
    }

    /**
     * STOP AGENT SERVICE
     */
    private fun stopAgent() {

        stopService(
            Intent(
                this,
                AgentService::class.java
            )
        )

        statusText.text =
            if (paired) {
                "🟢 PAIRED"
            } else {
                "🟡 WAITING FOR BROWSER PAIRING"
            }

        progressText.text =
            if (paired) {
                "Agent stopped. Press START AGENT to scan again."
            } else {
                "Waiting for browser pairing..."
            }

        progressBar.progress =
            0

        devicesText.text =
            "Devices found: 0"
    }

    /**
     * PERSISTENT AGENT ID
     */
    private fun getAgentId(): String {

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val existing =
            prefs.getString(
                AGENT_ID_KEY,
                null
            )

        if (
            !existing.isNullOrBlank()
        ) {
            return existing
        }

        val newId =
            "android-" +
            UUID.randomUUID()

        prefs.edit()
            .putString(
                AGENT_ID_KEY,
                newId
            )
            .apply()

        return newId
    }

    /**
     * PERSISTENT AGENT KEY
     */
    private fun getAgentKey(): String {

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val existing =
            prefs.getString(
                AGENT_KEY_KEY,
                null
            )

        if (
            !existing.isNullOrBlank()
        ) {
            return existing
        }

        val newKey =
            "NW-" +
            UUID.randomUUID()
                .toString()
                .uppercase()

        prefs.edit()
            .putString(
                AGENT_KEY_KEY,
                newKey
            )
            .apply()

        return newKey
    }

    /**
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

    /**
     * HTTP GET
     *
     * Returns:
     *
     * Pair(
     *     HTTP status code,
     *     response body
     * )
     */
    private fun getRequest(
        urlString: String
    ): Pair<Int, String> {

        var connection:
            HttpURLConnection? =
            null

        return try {

            val url =
                URL(urlString)

            connection =
                url.openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                10_000

            connection.readTimeout =
                10_000

            connection.useCaches =
                false

            connection.instanceFollowRedirects =
                true

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

            val body =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    ?: ""

            Pair(
                responseCode,
                body
            )

        } finally {

            connection?.disconnect()
        }
    }

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
            // Receiver was already unregistered.
        }

        super.onDestroy()
    }
}
