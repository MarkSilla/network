package com.netwatch.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var pairingRunnable: Runnable? = null

    private var pairedState = false
    private var agentRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

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

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL

        root.setPadding(
            32,
            40,
            32,
            32
        )

        root.setBackgroundColor(
            Color.rgb(10, 15, 25)
        )

        val scroll = ScrollView(this)

        scroll.addView(root)

        setContentView(scroll)

        val title = TextView(this)

        title.text = "NetWatch Android Agent"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        root.addView(
            title,
            marginParams(16)
        )

        val subtitle = TextView(this)

        subtitle.text =
            "Connect your Android device to the Network Dashboard"

        subtitle.textSize = 14f
        subtitle.setTextColor(Color.LTGRAY)
        subtitle.gravity = Gravity.CENTER

        root.addView(
            subtitle,
            marginParams(28)
        )

        val keyLabel = TextView(this)

        keyLabel.text = "AGENT KEY"
        keyLabel.textSize = 13f
        keyLabel.setTextColor(Color.GRAY)

        root.addView(
            keyLabel,
            marginParams(8)
        )

        agentKeyText = TextView(this)

        agentKeyText.textSize = 17f
        agentKeyText.setTextColor(Color.WHITE)

        agentKeyText.setPadding(
            18,
            18,
            18,
            18
        )

        agentKeyText.setBackgroundColor(
            Color.rgb(25, 35, 50)
        )

        root.addView(
            agentKeyText,
            marginParams(10)
        )

        val keyButtons = LinearLayout(this)

        keyButtons.orientation =
            LinearLayout.HORIZONTAL

        copyKeyButton = Button(this)

        copyKeyButton.text =
            "COPY AGENT KEY"

        generateKeyButton = Button(this)

        generateKeyButton.text =
            "GENERATE NEW KEY"

        keyButtons.addView(
            copyKeyButton,
            weightParams()
        )

        keyButtons.addView(
            generateKeyButton,
            weightParams()
        )

        root.addView(
            keyButtons,
            marginParams(18)
        )

        val idLabel = TextView(this)

        idLabel.text = "AGENT ID"
        idLabel.textSize = 13f
        idLabel.setTextColor(Color.GRAY)

        root.addView(
            idLabel,
            marginParams(8)
        )

        agentIdText = TextView(this)

        agentIdText.textSize = 13f
        agentIdText.setTextColor(Color.LTGRAY)

        root.addView(
            agentIdText,
            marginParams(12)
        )

        statusText = TextView(this)

        statusText.text =
            "🟡 STARTING"

        statusText.textSize = 18f
        statusText.gravity = Gravity.CENTER
        statusText.setTextColor(Color.WHITE)

        statusText.setPadding(
            12,
            20,
            12,
            20
        )

        root.addView(
            statusText,
            marginParams(12)
        )

        progressText = TextView(this)

        progressText.text =
            "Starting Android Agent..."

        progressText.textSize = 14f
        progressText.setTextColor(Color.LTGRAY)

        progressText.setPadding(
            10,
            10,
            10,
            20
        )

        root.addView(
            progressText,
            marginParams(12)
        )

        routerSection = LinearLayout(this)

        routerSection.orientation =
            LinearLayout.VERTICAL

        routerSection.setPadding(
            20,
            20,
            20,
            20
        )

        routerSection.setBackgroundColor(
            Color.rgb(20, 30, 42)
        )

        val routerTitle = TextView(this)

        routerTitle.text = "ROUTER"
        routerTitle.textSize = 14f
        routerTitle.setTextColor(Color.GRAY)

        routerSection.addView(
            routerTitle,
            marginParams(8)
        )

        routerIpInput = EditText(this)

        routerIpInput.hint =
            "Router IP e.g. 192.168.100.1"

        routerIpInput.setSingleLine(true)
        routerIpInput.setTextColor(Color.WHITE)
        routerIpInput.setHintTextColor(Color.GRAY)

        routerSection.addView(
            routerIpInput,
            marginParams(12)
        )

        root.addView(
            routerSection,
            marginParams(20)
        )

        startButton = Button(this)

        startButton.text =
            "START AGENT"

        root.addView(
            startButton,
            marginParams(12)
        )

        stopButton = Button(this)

        stopButton.text =
            "STOP AGENT"

        root.addView(
            stopButton,
            marginParams(12)
        )
    }

    private fun marginParams(
        bottom: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = bottom
        }
    }

    private fun weightParams():
            LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = 4
            marginEnd = 4
        }
    }

    private fun getPreferences() =
        getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private fun getAgentId(): String {

        val prefs = getPreferences()

        var agentId =
            prefs.getString(
                PREF_AGENT_ID,
                null
            )

        if (agentId.isNullOrBlank()) {

            agentId =
                "android-${UUID.randomUUID()}"

            prefs.edit()
                .putString(
                    PREF_AGENT_ID,
                    agentId
                )
                .apply()
        }

        return agentId
    }

    private fun getAgentKey(): String {

        val prefs = getPreferences()

        var agentKey =
            prefs.getString(
                PREF_AGENT_KEY,
                null
            )

        if (agentKey.isNullOrBlank()) {

            agentKey =
                "NW-${UUID.randomUUID().toString().uppercase()}"

            prefs.edit()
                .putString(
                    PREF_AGENT_KEY,
                    agentKey
                )
                .apply()
        }

        return agentKey
    }

    private fun loadSavedAgent() {
        getAgentId()
        getAgentKey()
    }

    private fun updateAgentKeyUI() {

        agentKeyText.text =
            getAgentKey()

        agentIdText.text =
            getAgentId()
    }

    private fun updatePairingUI() {

        if (pairedState) {

            statusText.text =
                "🟢 PAIRED"

            routerSection.visibility =
                View.VISIBLE

            startButton.visibility =
                View.VISIBLE

        } else {

            statusText.text =
                "🟡 WAITING FOR BROWSER PAIRING"

            routerSection.visibility =
                View.GONE

            startButton.visibility =
                View.GONE
        }
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

        stopAgent()

        pairedState = false
        agentRunning = false

        val newKey =
            "NW-${UUID.randomUUID().toString().uppercase()}"

        getPreferences()
            .edit()
            .putString(
                PREF_AGENT_KEY,
                newKey
            )
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
                    "Connecting..."
            }

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
                        "&hostname=$hostname" +
                        "&_t=${System.currentTimeMillis()}"

            for (attempt in 1..5) {

                try {

                    runOnUiThread {

                        statusText.text =
                            "🟡 CONNECTING"

                        progressText.text =
                            "Attempt $attempt/5"
                    }

                    val result =
                        getRequest(url)

                    val httpCode =
                        result.first

                    val responseBody =
                        result.second

                    if (httpCode !in 200..299) {

                        runOnUiThread {

                            statusText.text =
                                "🔴 SERVER HTTP ERROR"

                            progressText.text =
                                "HTTP $httpCode\n" +
                                responseBody.take(300)
                        }

                        if (attempt < 5) {
                            Thread.sleep(3000)
                        }

                        continue
                    }

                    val json =
                        parseServerResponse(
                            responseBody
                        )

                    if (json == null) {

                        runOnUiThread {

                            statusText.text =
                                "🔴 SERVER API ERROR"

                            progressText.text =
                                "HTTP $httpCode\n" +
                                "The pairing API returned HTML instead of JSON.\n\n" +
                                responseBody
                                    .replace(
                                        "\n",
                                        " "
                                    )
                                    .take(300)
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

                        if (attempt < 5) {
                            Thread.sleep(3000)
                        }

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
                            "Copy the Agent Key and pair it from the browser."
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

                    if (attempt < 5) {
                        Thread.sleep(3000)
                    }
                }
            }

            runOnUiThread {

                statusText.text =
                    "🔴 DASHBOARD CONNECTION FAILED"

                progressText.text =
                    "Unable to connect after 5 attempts."
            }

        }.start()
    }

    private fun parseServerResponse(
        body: String
    ): JSONObject? {

        val trimmed =
            body.trim()

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

        pairingRunnable =
            object : Runnable {

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

                val url =
                    "$DASHBOARD_URL/api/agent/pairing-status" +
                            "?agentId=${encode(getAgentId())}" +
                            "&agentKey=${encode(getAgentKey())}" +
                            "&hostname=${encode(android.os.Build.MODEL ?: "Android Agent")}" +
                            "&_t=${System.currentTimeMillis()}"

                val result =
                    getRequest(url)

                val code =
                    result.first

                val body =
                    result.second

                if (code !in 200..299) {

                    runOnUiThread {

                        progressText.text =
                            "Pairing HTTP $code"
                    }

                    return@Thread
                }

                val json =
                    parseServerResponse(body)

                if (json == null) {

                    runOnUiThread {

                        statusText.text =
                            "🔴 SERVER API ERROR"

                        progressText.text =
                            "Pairing API returned HTML instead of JSON."
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
                        "Pairing error:\n" +
                        "${e.javaClass.simpleName}: " +
                        "${e.message}"
                }
            }

        }.start()
    }

    private fun startAgent() {

        if (!pairedState) {

            progressText.text =
                "Pair the Android Agent first."

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

            if (
                android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.O
            ) {

                startForegroundService(
                    intent
                )

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
                "no-cache, no-store, max-age=0"
            )

            connection.setRequestProperty(
                "Pragma",
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
                    ).use {
                        it.readText()
                    }

                } else {
                    ""
                }

            Pair(
                code,
                body
            )

        } finally {

            connection?.disconnect()
        }
    }

    private fun encode(
        value: String
    ): String {

        return URLEncoder.encode(
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
