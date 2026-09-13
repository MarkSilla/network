package com.netwatch.agent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var dashboardUrl by mutableStateOf("")
    private var routerIp by mutableStateOf("")
    private var agentKey by mutableStateOf("")
    private var status by mutableStateOf("Disconnected")
    private var isTesting by mutableStateOf(false)

    private val prefs by lazy {
        getSharedPreferences("netwatch_agent", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dashboardUrl = prefs.getString("dashboard_url", "") ?: ""
        routerIp = prefs.getString("router_ip", "") ?: ""
        agentKey = prefs.getString("agent_key", "") ?: ""

        if (prefs.getString("agent_id", null) == null) {
            prefs.edit()
                .putString("agent_id", UUID.randomUUID().toString())
                .apply()
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {

                        Text(
                            text = "NetWatch Android Agent",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = dashboardUrl,
                            onValueChange = {
                                dashboardUrl = it
                            },
                            label = {
                                Text("Dashboard URL")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = routerIp,
                            onValueChange = {
                                routerIp = it
                            },
                            label = {
                                Text("Router IP")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = agentKey,
                            onValueChange = {
                                agentKey = it
                            },
                            label = {
                                Text("Agent Key")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Status",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(status)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                testConnection()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isTesting
                        ) {
                            Text(
                                if (isTesting)
                                    "Testing..."
                                else
                                    "Test Dashboard Connection"
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                startAgent()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Agent")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                stopAgent()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Agent")
                        }
                    }
                }
            }
        }
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("dashboard_url", dashboardUrl.trim())
            .putString("router_ip", routerIp.trim())
            .putString("agent_key", agentKey.trim())
            .apply()
    }

    private fun testConnection() {

        val cleanUrl = dashboardUrl.trim().removeSuffix("/")
        val cleanRouterIp = routerIp.trim()
        val cleanAgentKey = agentKey.trim()

        if (cleanUrl.isEmpty()) {
            status = "ERROR: Dashboard URL is empty"
            return
        }

        if (cleanRouterIp.isEmpty()) {
            status = "ERROR: Router IP is empty"
            return
        }

        if (cleanAgentKey.isEmpty()) {
            status = "ERROR: Agent Key is empty"
            return
        }

        saveSettings()

        isTesting = true
        status = "Connecting..."

        lifecycleScope.launch {

            val result = withContext(Dispatchers.IO) {
                registerAgent(
                    cleanUrl,
                    cleanRouterIp,
                    cleanAgentKey
                )
            }

            isTesting = false
            status = result

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

            val agentId = prefs.getString(
                "agent_id",
                UUID.randomUUID().toString()
            ) ?: UUID.randomUUID().toString()

            prefs.edit()
                .putString("agent_id", agentId)
                .apply()

            val url = URL("$baseUrl/api/agent/register")

            connection = url.openConnection() as HttpURLConnection

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

            val body = JSONObject().apply {
                put("agentId", agentId)
                put("routerIp", routerIp)
                put("agentKey", key)
                put("hostname", android.os.Build.MODEL)
            }.toString()

            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode

            val responseBody = try {

                val stream =
                    if (responseCode >= 400)
                        connection.errorStream
                    else
                        connection.inputStream

                stream?.bufferedReader()?.use {
                    it.readText()
                } ?: ""

            } catch (e: Exception) {
                ""
            }

            if (responseCode in 200..299) {

                "CONNECTED • Dashboard OK"

            } else {

                val serverMessage =
                    try {
                        val json = JSONObject(responseBody)

                        json.optString(
                            "error",
                            json.optString(
                                "message",
                                responseBody
                            )
                        )

                    } catch (_: Exception) {
                        responseBody
                    }

                when {

                    responseCode == 401 ->
                        "ERROR 401 • Unauthorized\n$serverMessage"

                    responseCode == 403 ->
                        "ERROR 403 • Server rejected request\n$serverMessage"

                    responseCode == 404 ->
                        "ERROR 404 • Register endpoint not found\n$serverMessage"

                    responseCode >= 500 ->
                        "ERROR $responseCode • Server error\n$serverMessage"

                    else ->
                        "ERROR $responseCode\n$serverMessage"
                }
            }

        } catch (e: java.net.UnknownHostException) {

            "ERROR • Cannot find dashboard server"

        } catch (e: java.net.SocketTimeoutException) {

            "ERROR • Connection timeout"

        } catch (e: Exception) {

            "ERROR • ${e.javaClass.simpleName}: ${e.message}"

        } finally {
            connection?.disconnect()
        }
    }

    private fun startAgent() {

        saveSettings()

        val intent = Intent(
            this,
            AgentService::class.java
        )

        startForegroundService(intent)

        status = "Agent starting..."

        Toast.makeText(
            this,
            "Android Agent started",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun stopAgent() {

        val intent = Intent(
            this,
            AgentService::class.java
        )

        stopService(intent)

        status = "Disconnected"

        Toast.makeText(
            this,
            "Android Agent stopped",
            Toast.LENGTH_SHORT
        ).show()
    }
}

Important: kailangan ng project mo ang "lifecycle-runtime-ktx" dahil gumagamit ito ng "lifecycleScope". Kung meron na iyon sa "build.gradle.kts", okay na.

Sa GitHub phone mo:

"app → src → main → java → com → netwatch → agent → MainActivity.kt"

→ Edit
→ "Ctrl+A" / select all
→ paste itong code
→ Commit changes.

Pag na-paste mo na, sabihin mo “next” at tutulungan kitang i-build yung bagong APK.
