package com.netwatch.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        private const val DASHBOARD_URL =
            "https://network-device-dashboard-tydeft.v2.appdeploy.ai"

        private const val PREFS_NAME = "netwatch_agent"
        private const val PREF_AGENT_ID = "agent_id"
        private const val PREF_AGENT_KEY = "agent_key"
    }

    private lateinit var agentKeyText: TextView
    private lateinit var agentIdText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressText: TextView

    private lateinit var routerSection: LinearLayout
    private lateinit var routerIpInput: EditText

    private lateinit var copyKeyButton: Button
    private lateinit var generateKeyButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())

    private var pairingRunnable: Runnable? = null

    private var pairedState = false
    private var agentRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        initializeViews()
        loadSavedAgent()

        updateAgentKeyUI()
        updatePairingUI()

        copyKeyButton.setOnClickListener {
            copyAgentKey()
        }

        generateKeyButton.setOnClickListener {
            regenerateAgentKey()
        }

        startButton.setOnClickListener {
            startAgent()
        }

        stopButton.setOnClickListener {
            stopAgent()
        }

        announceAgent()
    }

    private fun initializeViews() {

        agentKeyText = findViewById(R.id.agentKeyText)
        agentIdText = findViewById(R.id.agentIdText)
        statusText = findViewById(R.id.statusText)
        progressText = findViewById(R.id.progressText)

        routerSection = findViewById(R.id.routerSection)
        routerIpInput = findViewById(R.id.routerIpInput)

        copyKeyButton = findViewById(R.id.copyKeyButton)
        generateKeyButton = findViewById(R.id.generateKeyButton)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
    }

    private fun getPreferences() =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getAgentId(): String {

        val prefs = getPreferences()

        var agentId = prefs.getString(PREF_AGENT_ID, null)

        if (agentId.isNullOrBlank()) {

            agentId = "android-${UUID.randomUUID()}"

            prefs.edit()
                .putString(PREF_AGENT_ID, agentId)
                .apply()
        }

        return agentId
    }

    private fun getAgentKey(): String {

        val prefs = getPreferences()

        var agentKey = prefs.getString(PREF_AGENT_KEY, null)

        if (agentKey.isNullOrBlank()) {

            agentKey =
                "NW-${UUID.randomUUID().toString().uppercase()}"

            prefs.edit()
                .putString(PREF_AGENT_KEY, agentKey)
                .apply()
        }

        return agentKey
    }

    private fun loadSavedAgent() {

        val agentId = getAgentId()
        val agentKey = getAgentKey()

        agentIdText.text = agentId
        agentKeyText.text = agentKey
    }

    private fun updateAgentKeyUI() {

        agentKeyText.text = getAgentKey()
        agentIdText.text = getAgentId()
    }

    private fun updatePairingUI() {

        if (pairedState) {

            statusText.text = "🟢 PAIRED"

            routerSection.visibility = View.VISIBLE

            startButton.visibility = View.VISIBLE

        } else {

            statusText.text = "🟡 WAITING FOR BROWSER PAIRING"

            routerSection.visibility = View.GONE

            startButton.visibility = View.GONE
        }
    }

    private fun copyAgentKey() {

        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager

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

        stopAgent()

        pairedState = false
        agentRunning = false

        val newKey =
            "NW-${UUID.randomUUID().toString().uppercase()}"

        getPreferences()
            .edit()
            .putString(PREF_AGENT_KEY, newKey)
            .apply()

        updateAgentKeyUI()
        updatePairingUI()

        progressText.text =
            "New Agent Key generated. Waiting for browser pairing..."

        announceAgent()
    }

    private fun announceAgent() {

        Thread {

            runOnUiThread {

                statusText.text =
                    "🟡 CONNECTING TO DASHBOARD"

                progressText.text =
                    "Connecting to AppDeploy dashboard..."
            }

            val agentId = getAgentId()
            val agentKey = getAgentKey()

            val hostname =
                android.os.Build.MODEL ?: "Android Agent"

            val encodedAgentId =
                encode(agentId)

            val encodedAgentKey =
                encode(agentKey)

            val encodedHostname =
                encode(hostname)

            val url =
                "$DASHBOARD_URL/api/agent/pairing-status" +
                        "?agentId=$encodedAgentId" +
                        "&agentKey=$encodedAgentKey" +
                        "&hostname=$encodedHostname"

            for (attempt in 1..5) {

                try {

                    runOnUiThread {

                        statusText.text =
                            "🟡 RETRYING CONNECTION"

                        progressText.text =
                            "Attempt $attempt/5"
                    }

                    val result =
                        getRequest(url)

                    val httpCode = result.first
                    val responseBody = result.second

                    /*
                     * IMPORTANT:
                     *
                     * The previous error was:
                     *
                     * Value <!doctype of type java.lang.String
                     * cannot be converted to JSONObject
                     *
                     * That means the server returned HTML,
                     * not JSON.
                     *
                     * NEVER call JSONObject() blindly.
                     */

                    if (httpCode !in 200..299) {

                        runOnUiThread {

                            statusText.text =
                                "🔴 SERVER HTTP ERROR"

                            progressText.text =
                                "HTTP $httpCode\n" +
                                responseBody.take(250)
                        }

                        Thread.sleep(3000)
                        continue
                    }

                    val json =
                        parseServerResponse(responseBody)

                    if (json == null) {

                        runOnUiThread {

                            statusText.text =
                                "🔴 INVALID SERVER RESPONSE"

                            progressText.text =
                                "HTTP $httpCode\n" +
                                "Expected JSON but received:\n" +
                                responseBody
                                    .replace("\n", " ")
                                    .take(250)
                        }

                        Thread.sleep(3000)
                        continue
                    }

                    val registered =
                        json.optBoolean(
                            "registered",
                            false
                        )

                    val paired =
                        json.optBoolean(
                            "paired",
                            false
                        )

                    if (!registered) {

                        runOnUiThread {

                            statusText.text =
                                "🔴 AGENT NOT REGISTERED"

                            progressText.text =
                                json.toString()
                        }

                        Thread.sleep(3000)
                        continue
                    }

                    pairedState = paired

                    if (paired) {

                        runOnUiThread {

                            statusText.text =
                                "🟢 PAIRED"

                            progressText.text =
                                "Android Agent paired successfully."

                            routerSection.visibility =
                                View.VISIBLE

                            startButton.visibility =
                                View.VISIBLE
                        }

                        startPairingPolling()

                        return@Thread
                    }

                    runOnUiThread {

                        statusText.text =
                            "🟡 WAITING FOR BROWSER PAIRING"

                        progressText.text =
                            "Agent Key is registered.\n" +
                            "Paste the key into the browser dashboard."
                    }

                    startPairingPolling()

                    return@Thread

                } catch (e: Exception) {

                    runOnUiThread {

                        statusText.text =
                            "🟡 RETRYING CONNECTION"

                        progressText.text =
                            "Attempt $attempt/5\n" +
                            "${e.javaClass.simpleName}: " +
                            "${e.message ?: "Unknown error"}"
                    }

                    try {
                        Thread.sleep(3000)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            }

            runOnUiThread {

                statusText.text =
                    "🔴 DASHBOARD CONNECTION FAILED"

                progressText.text =
                    "Unable to receive a valid JSON response after 5 attempts."
            }

        }.start()
    }

    private fun parseServerResponse(
        body: String
    ): JSONObject? {

        val trimmed =
            body.trim()

        /*
         * JSON object should begin with {
         *
         * If it begins with:
         *
         * <!doctype
         *
         * then the server returned the website HTML.
         */

        if (!trimmed.startsWith("{")) {

            return null
        }

        return try {

            JSONObject(trimmed)

        } catch (_: Exception) {

            null
        }
    }

    private fun startPairingPolling() {

        pairingRunnable?.let {
            mainHandler.removeCallbacks(it)
        }

        pairingRunnable = object : Runnable {

            override fun run() {

                checkPairing()

                mainHandler.postDelayed(
                    this,
                    3000
                )
            }
        }

        mainHandler.post(
            pairingRunnable!!
        )
    }

    private fun checkPairing() {

        Thread {

            try {

                val agentId =
                    encode(getAgentId())

                val agentKey =
                    encode(getAgentKey())

                val hostname =
                    encode(
                        android.os.Build.MODEL
                            ?: "Android Agent"
                    )

                val url =
                    "$DASHBOARD_URL/api/agent/pairing-status" +
                            "?agentId=$agentId" +
                            "&agentKey=$agentKey" +
                            "&hostname=$hostname"

                val result =
                    getRequest(url)

                val code =
                    result.first

                val body =
                    result.second

                if (code !in 200..299) {

                    runOnUiThread {

                        progressText.text =
                            "Pairing check HTTP $code"
                    }

                    return@Thread
                }

                val json =
                    parseServerResponse(body)

                if (json == null) {

                    runOnUiThread {

                        statusText.text =
                            "🔴 SERVER RETURNED HTML"

                        progressText.text =
                            body
                                .replace("\n", " ")
                                .take(250)
                    }

                    return@Thread
                }

                val registered =
                    json.optBoolean(
                        "registered",
                        false
                    )

                val paired =
                    json.optBoolean(
                        "paired",
                        false
                    )

                if (!registered) {

                    runOnUiThread {

                        statusText.text =
                            "🔴 NOT REGISTERED"

                        progressText.text =
                            json.toString()
                    }

                    return@Thread
                }

                if (paired) {

                    pairedState = true

                    runOnUiThread {

                        statusText.text =
                            "🟢 PAIRED"

                        progressText.text =
                            "Dashboard pairing approved."

                        routerSection.visibility =
                            View.VISIBLE

                        startButton.visibility =
                            View.VISIBLE
                    }

                } else {

                    pairedState = false

                    runOnUiThread {

                        statusText.text =
                            "🟡 WAITING FOR BROWSER PAIRING"

                        routerSection.visibility =
                            View.GONE

                        startButton.visibility =
                            View.GONE
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    progressText.text =
                        "Pairing check failed:\n" +
                        "${e.javaClass.simpleName}: " +
                        "${e.message ?: "Unknown error"}"
                }
            }

        }.start()
    }

    private fun startAgent() {

        if (!pairedState) {

            progressText.text =
                "Pair the Android Agent in the browser first."

            return
        }

        val routerIp =
            routerIpInput.text
                .toString()
                .trim()

        if (routerIp.isBlank()) {

            routerIpInput.error =
                "Router IP is required"

            return
        }

        val intent =
            Intent(
                this,
                AgentService::class.java
            )

        intent.putExtra(
            "agentId",
            getAgentId()
        )

        intent.putExtra(
            "agentKey",
            getAgentKey()
        )

        intent.putExtra(
            "routerIp",
            routerIp
        )

        intent.putExtra(
            "dashboardUrl",
            DASHBOARD_URL
        )

        try {

            if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.O
            ) {

                startForegroundService(intent)

            } else {

                startService(intent)
            }

            agentRunning = true

            statusText.text =
                "🟢 AGENT RUNNING"

            progressText.text =
                "Scanning LAN devices..."

            startButton.visibility =
                View.GONE

            stopButton.visibility =
                View.VISIBLE

        } catch (e: Exception) {

            statusText.text =
                "🔴 AGENT START FAILED"

            progressText.text =
                "${e.javaClass.simpleName}: " +
                "${e.message}"
        }
    }

    private fun stopAgent() {

        try {

            stopService(
                Intent(
                    this,
                    AgentService::class.java
                )
            )

        } catch (_: Exception) {
        }

        agentRunning = false

        if (pairedState) {

            statusText.text =
                "🟢 PAIRED"

            progressText.text =
                "Agent stopped."

            startButton.visibility =
                View.VISIBLE

            stopButton.visibility =
                View.GONE

        } else {

            updatePairingUI()
        }
    }

    private fun getRequest(
        urlString: String
    ): Pair<Int, String> {

        var connection:
                HttpURLConnection? = null

        return try {

            val url =
                URL(urlString)

            connection =
                url.openConnection()
                        as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                10000

            connection.readTimeout =
                10000

            connection.useCaches =
                false

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Cache-Control",
                "no-cache"
            )

            val code =
                connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val body =
                if (stream != null) {

                    BufferedReader(
                        InputStreamReader(stream)
                    ).use { reader ->

                        reader
                            .readText()
                    }

                } else {
                    ""
                }

            Pair(code, body)

        } finally {

            connection?.disconnect()
        }
    }

    private fun encode(
        value: String
    ): String {

        return URLEncoder
            .encode(
                value,
                "UTF-8"
            )
    }

    override fun onDestroy() {

        pairingRunnable?.let {
            mainHandler.removeCallbacks(it)
        }

        super.onDestroy()
    }
}
