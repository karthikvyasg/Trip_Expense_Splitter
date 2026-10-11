package com.example.tripexpensesplitter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyManager: TripHistoryManager
    private val historyList = mutableListOf<TripHistoryItem>()
    private lateinit var adapter: HistoryAdapter

    private lateinit var tvEmptyHistory: TextView
    private lateinit var lvTripHistory: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayUtils.enableMaxRefreshRate(this)
        setContentView(R.layout.activity_history)

        historyManager = TripHistoryManager(this)

        val btnHistoryBack = findViewById<ImageButton>(R.id.btnHistoryBack)
        val btnClearAllHistory = findViewById<Button>(R.id.btnClearAllHistory)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)
        lvTripHistory = findViewById(R.id.lvTripHistory)

        btnHistoryBack.setOnClickListener { finish() }

        adapter = HistoryAdapter()
        lvTripHistory.adapter = adapter

        btnClearAllHistory.setOnClickListener {
            if (historyList.isEmpty()) {
                Toast.makeText(this, "History is already empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Clear All History?")
                .setMessage("Are you sure you want to delete all past trips? This cannot be undone.")
                .setPositiveButton("Clear All") { _, _ ->
                    historyManager.clearAllHistory()
                    loadHistory()
                    Toast.makeText(this, "All history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadHistory()
    }

    private fun loadHistory() {
        historyList.clear()
        historyList.addAll(historyManager.getHistory())

        if (historyList.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE
            lvTripHistory.visibility = View.GONE
        } else {
            tvEmptyHistory.visibility = View.GONE
            lvTripHistory.visibility = View.VISIBLE
        }
        adapter.notifyDataSetChanged()
    }

    inner class HistoryAdapter : BaseAdapter() {
        override fun getCount(): Int = historyList.size
        override fun getItem(position: Int): Any = historyList[position]
        override fun getItemId(position: Int): Long = historyList[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@HistoryActivity)
                .inflate(R.layout.item_trip_history, parent, false)

            val item = historyList[position]

            val tvTitle = view.findViewById<TextView>(R.id.tvHistoryTitle)
            val tvDate = view.findViewById<TextView>(R.id.tvHistoryDate)
            val tvTotal = view.findViewById<TextView>(R.id.tvHistoryTotal)
            val tvPerPerson = view.findViewById<TextView>(R.id.tvHistoryPerPerson)
            val tvSettlements = view.findViewById<TextView>(R.id.tvHistorySettlements)
            val btnDeleteTrip = view.findViewById<ImageButton>(R.id.btnDeleteTrip)

            tvTitle.text = item.title
            tvDate.text = item.date
            tvTotal.text = "Total: ₹%.2f".format(item.totalAmount)
            val perPerson = if (item.memberCount > 0) item.totalAmount / item.memberCount else 0.0
            tvPerPerson.text = "₹%.2f / person".format(perPerson)

            tvSettlements.text = item.settlements.joinToString("\n") { "• $it" }

            btnDeleteTrip.setOnClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("Delete Trip")
                    .setMessage("Remove '${item.title}' from history?")
                    .setPositiveButton("Delete") { _, _ ->
                        historyManager.deleteTrip(item.id)
                        loadHistory()
                        Toast.makeText(this@HistoryActivity, "Trip deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            return view
        }
    }
}