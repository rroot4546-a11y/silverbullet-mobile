package com.silverbullet.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.silverbullet.mobile.Prefs
import com.silverbullet.mobile.databinding.ActivitySettingsBinding
import com.silverbullet.mobile.net.ServerClient

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.etServerUrl.setText(Prefs.serverUrl)
        binding.etToken.setText(Prefs.bearerToken)

        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnSave.setOnClickListener { saveAndClose() }
    }

    private fun testConnection() {
        val url = binding.etServerUrl.text.toString().trim()
        if (url.isBlank()) {
            Toast.makeText(this, "Enter the server URL", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnTestConnection.isEnabled = false
        binding.tvTestResult.text = "Testing…"
        ServerClient.ping(url, binding.etToken.text.toString().trim()) { result ->
            runOnUiThread {
                binding.btnTestConnection.isEnabled = true
                binding.tvTestResult.text = when {
                    result.ok && result.serverVersion != null ->
                        "Connected — server v${result.serverVersion}"
                    result.ok -> "Connected"
                    else -> "Failed: ${result.error}"
                }
            }
        }
    }

    private fun saveAndClose() {
        val url = binding.etServerUrl.text.toString().trim()
        if (url.isBlank()) {
            Toast.makeText(this, "Server URL cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        val token = binding.etToken.text.toString().trim()
        val changed = url != Prefs.serverUrl || token != Prefs.bearerToken
        Prefs.serverUrl = url
        Prefs.bearerToken = token
        setResult(RESULT_OK, Intent().putExtra("changed", changed))
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}