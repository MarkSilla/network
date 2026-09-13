package com.netwatch.agent

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var dashboard: EditText
    private lateinit var router: EditText
    private lateinit var key: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("agent", MODE_PRIVATE)
        dashboard = field("Dashboard URL", prefs.getString("dashboard", "") ?: "")
        router = field("Router IP", prefs.getString("router", "") ?: "")
        key = field("Agent Key", prefs.getString("key", "") ?: "")
        status = TextView(this).apply { text = "Stopped"; textSize = 16f; setPadding(0, 16, 0, 16) }

        val start = Button(this).apply {
            text = "Start Agent"
            setOnClickListener {
                prefs.edit().putString("dashboard", dashboard.text.toString().trim())
                    .putString("router", router.text.toString().trim())
                    .putString("key", key.text.toString().trim()).apply()
                startForegroundService(Intent(this@MainActivity, AgentService::class.java).apply { action = AgentService.START })
                status.text = "Agent running"
            }
        }
        val stop = Button(this).apply {
            text = "Stop Agent"
            setOnClickListener {
                stopService(Intent(this@MainActivity, AgentService::class.java))
                status.text = "Stopped"
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
            addView(TextView(context).apply { text = "NetWatch Android Agent"; textSize = 24f })
            addView(TextView(context).apply { text = "Connect this phone to the same Wi-Fi as the monitored router."; setPadding(0, 8, 0, 20) })
            addView(dashboard)
            addView(router)
            addView(key)
            addView(start)
            addView(stop)
            addView(status)
        }
        setContentView(root)
    }

    private fun field(label: String, value: String) = EditText(this).apply {
        hint = label
        setText(value)
        setPadding(0, 14, 0, 14)
    }
}
