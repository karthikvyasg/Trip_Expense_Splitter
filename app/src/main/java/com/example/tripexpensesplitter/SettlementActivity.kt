package com.example.tripexpensesplitter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettlementActivity : AppCompatActivity() {

    private lateinit var tripTitle: String
    private var totalAmount: Double = 0.0
    private var memberCount: Int = 0
    private var settlements: ArrayList<String> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayUtils.enableMaxRefreshRate(this)
        setContentView(R.layout.activity_settlement)

        val tripId = intent.getLongExtra("TRIP_ID", System.currentTimeMillis())
        tripTitle = intent.getStringExtra("TRIP_TITLE") ?: "Trip Expense"
        totalAmount = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0)
        memberCount = intent.getIntExtra("MEMBER_COUNT", 1)
        settlements = intent.getStringArrayListExtra("SETTLEMENTS") ?: arrayListOf()

        val currentDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        // Save trip directly into persistent history
        val historyManager = TripHistoryManager(this)
        historyManager.saveTrip(
            TripHistoryItem(
                id = tripId,
                title = tripTitle,
                date = currentDate,
                totalAmount = totalAmount,
                memberCount = memberCount,
                settlements = settlements
            )
        )

        val btnSettlementBack = findViewById<ImageButton>(R.id.btnSettlementBack)
        val tvSettlementTitle = findViewById<TextView>(R.id.tvSettlementTitle)
        val tvFinalTotal = findViewById<TextView>(R.id.tvFinalTotal)
        val tvFinalPerPerson = findViewById<TextView>(R.id.tvFinalPerPerson)
        val lvSettlements = findViewById<ListView>(R.id.lvSettlements)
        val btnShareText = findViewById<Button>(R.id.btnShareText)
        val btnExportCSV = findViewById<Button>(R.id.btnExportCSV)
        val btnDone = findViewById<Button>(R.id.btnDone)

        tvSettlementTitle.text = tripTitle
        tvFinalTotal.text = "₹%.2f".format(totalAmount)
        val perPerson = if (memberCount > 0) totalAmount / memberCount else 0.0
        tvFinalPerPerson.text = "₹%.2f".format(perPerson)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, settlements)
        lvSettlements.adapter = adapter

        btnSettlementBack.setOnClickListener { finish() }
        btnDone.setOnClickListener { finish() }

        btnShareText.setOnClickListener {
            shareAsPlainText(currentDate, perPerson)
        }

        btnExportCSV.setOnClickListener {
            exportAndShareCSV(currentDate, perPerson)
        }
    }

    private fun shareAsPlainText(currentDate: String, perPerson: Double) {
        val shareText = StringBuilder()
        shareText.append("🧾 *Trip Settlement: $tripTitle*\n")
        shareText.append("📅 Date: $currentDate\n")
        shareText.append("💰 Total Spent: ₹%.2f\n".format(totalAmount))
        shareText.append("👥 Members: $memberCount (₹%.2f/person)\n\n".format(perPerson))
        shareText.append("*Settlements:*\n")
        settlements.forEach { shareText.append("• $it\n") }
        shareText.append("\n_Calculated with Trip Expense Splitter_")

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText.toString())
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share Settlement Summary"))
    }

    private fun exportAndShareCSV(currentDate: String, perPerson: Double) {
        try {
            val fileName = "${tripTitle.replace(" ", "_")}_Settlement.csv"
            val file = File(cacheDir, fileName)
            val writer = FileWriter(file)

            writer.append("Trip Name,Date,Total Amount,Members Count,Fair Share Per Person\n")
            writer.append("\"$tripTitle\",\"$currentDate\",${"%.2f".format(totalAmount)},$memberCount,${"%.2f".format(perPerson)}\n\n")
            writer.append("Settlement Transaction Details\n")

            settlements.forEach { line ->
                writer.append("\"$line\"\n")
            }

            writer.flush()
            writer.close()

            val uri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "$tripTitle - Expense Report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export Expense CSV"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to generate CSV export", Toast.LENGTH_SHORT).show()
        }
    }
}