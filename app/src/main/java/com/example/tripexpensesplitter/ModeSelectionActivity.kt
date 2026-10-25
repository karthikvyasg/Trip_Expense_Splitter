package com.example.tripexpensesplitter

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.card.MaterialCardView

class ModeSelectionActivity : AppCompatActivity() {

    private var selectedMode: SplitMode = SplitMode.EQUAL

    enum class SplitMode {
        EQUAL, UNEQUAL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() for Theme.App.Starting
        val splashScreen = installSplashScreen()

        // Enforce Light Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        DisplayUtils.enableMaxRefreshRate(this)
        setContentView(R.layout.activity_mode_selection)

        val cardEqual = findViewById<MaterialCardView>(R.id.cardEqualSplit)
        val cardUnequal = findViewById<MaterialCardView>(R.id.cardUnequalSplit)
        val btnContinue = findViewById<Button>(R.id.btnContinueMode)

        fun updateCardSelection() {
            if (selectedMode == SplitMode.EQUAL) {
                cardEqual.strokeColor = Color.parseColor("#0D6EFD")
                cardEqual.strokeWidth = (2 * resources.displayMetrics.density).toInt()

                cardUnequal.strokeColor = Color.parseColor("#DEE2E6")
                cardUnequal.strokeWidth = (1 * resources.displayMetrics.density).toInt()

                btnContinue.text = "Continue with Equal Split"
            } else {
                cardUnequal.strokeColor = Color.parseColor("#0D6EFD")
                cardUnequal.strokeWidth = (2 * resources.displayMetrics.density).toInt()

                cardEqual.strokeColor = Color.parseColor("#DEE2E6")
                cardEqual.strokeWidth = (1 * resources.displayMetrics.density).toInt()

                btnContinue.text = "Continue with Unequal Split"
            }
        }

        cardEqual.setOnClickListener {
            selectedMode = SplitMode.EQUAL
            updateCardSelection()
        }

        cardUnequal.setOnClickListener {
            selectedMode = SplitMode.UNEQUAL
            updateCardSelection()
        }

        btnContinue.setOnClickListener {
            if (selectedMode == SplitMode.EQUAL) {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            } else {
                val intent = Intent(this, UnequalSplitActivity::class.java)
                startActivity(intent)
            }
        }

        updateCardSelection()
    }
}