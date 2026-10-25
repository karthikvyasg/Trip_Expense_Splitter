package com.example.tripexpensesplitter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val btnBack = findViewById<ImageButton>(R.id.btnBackAbout)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)
        val btnRateUs = findViewById<Button>(R.id.btnRateUs)

        // Automatically pulls the version name directly from build.gradle.kts
        tvAppVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        btnBack.setOnClickListener {
            finish()
        }

        btnRateUs.setOnClickListener {
            val appPackageName = packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            }
        }
    }
}