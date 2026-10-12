package com.example.tripexpensesplitter

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private var tripSessionId: Long = 0L
    private val memberList = ArrayList<String>()
    private val rawInputMap = mutableMapOf<String, String>()
    private val spendingMap = mutableMapOf<String, Double>()
    private lateinit var adapter: SingleScreenAdapter

    private lateinit var etTripTitle: EditText
    private lateinit var etMemberName: EditText
    private lateinit var tvLiveTotal: TextView
    private lateinit var tvLivePerPerson: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply persisted user theme preference before rendering
        ThemeManager.applySavedTheme(this)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        DisplayUtils.enableMaxRefreshRate(this)
        setContentView(R.layout.activity_main)

        tripSessionId = System.currentTimeMillis()

        etTripTitle = findViewById(R.id.etTripTitle)
        etMemberName = findViewById(R.id.etMemberName)
        val btnAddMember = findViewById<Button>(R.id.btnAddMember)
        val lvMembersSpending = findViewById<ListView>(R.id.lvMembersSpending)
        val btnCalculateSplit = findViewById<Button>(R.id.btnCalculateSplit)
        val btnViewHistory = findViewById<Button>(R.id.btnViewHistory)
        val btnResetTrip = findViewById<Button>(R.id.btnResetTrip)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)

        tvLiveTotal = findViewById(R.id.tvLiveTotal)
        tvLivePerPerson = findViewById(R.id.tvLivePerPerson)

        adapter = SingleScreenAdapter()
        lvMembersSpending.adapter = adapter

        // Setup 3-line popup menu for theme mode shifting
        btnMenu.setOnClickListener { view ->
            showThemeMenu(view)
        }

        btnAddMember.setOnClickListener {
            val name = etMemberName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (memberList.contains(name)) {
                Toast.makeText(this, "Member already added", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            memberList.add(name)
            rawInputMap[name] = ""
            spendingMap[name] = 0.0
            adapter.notifyDataSetChanged()
            updateLiveSummary()
            etMemberName.text.clear()
        }

        btnCalculateSplit.setOnClickListener {
            if (memberList.size < 2) {
                Toast.makeText(this, "Add at least 2 members to split expenses!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalSpent = spendingMap.values.sum()
            if (totalSpent <= 0.0) {
                Toast.makeText(this, "Please enter at least one spending amount!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val settlements = calculateSettlements(memberList, spendingMap)
            val title = etTripTitle.text.toString().trim()

            val intent = Intent(this, SettlementActivity::class.java).apply {
                putExtra("TRIP_ID", tripSessionId)
                putExtra("TRIP_TITLE", if (title.isEmpty()) "Trip Expense" else title)
                putExtra("TOTAL_AMOUNT", totalSpent)
                putExtra("MEMBER_COUNT", memberList.size)
                putStringArrayListExtra("SETTLEMENTS", ArrayList(settlements))
            }
            startActivity(intent)
        }

        btnViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        btnResetTrip.setOnClickListener {
            if (memberList.isEmpty() && etTripTitle.text.isBlank()) {
                Toast.makeText(this, "Everything is already cleared", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Start a New Trip?")
                .setMessage("This will clear the current trip title, members, and spending entries.")
                .setPositiveButton("Reset") { _, _ ->
                    resetAllInputs()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updateLiveSummary()
    }

    private fun showThemeMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, ThemeManager.THEME_LIGHT, 0, "☀️ Light Mode")
        popup.menu.add(0, ThemeManager.THEME_DARK, 1, "🌙 Dark Mode")
        popup.menu.add(0, ThemeManager.THEME_SYSTEM, 2, "📱 System Default")

        popup.setOnMenuItemClickListener { item ->
            ThemeManager.setTheme(this, item.itemId)
            true
        }
        popup.show()
    }

    private fun resetAllInputs() {
        tripSessionId = System.currentTimeMillis()
        etTripTitle.text.clear()
        etMemberName.text.clear()
        memberList.clear()
        rawInputMap.clear()
        spendingMap.clear()
        adapter.notifyDataSetChanged()
        updateLiveSummary()
        Toast.makeText(this, "Started fresh trip", Toast.LENGTH_SHORT).show()
    }

    private fun updateLiveSummary() {
        val total = spendingMap.values.sum()
        val count = memberList.size
        val perPerson = if (count > 0) total / count else 0.0

        tvLiveTotal.text = "₹%.2f".format(total)
        tvLivePerPerson.text = "₹%.2f".format(perPerson)
    }

    private fun parseSum(expression: String): Double {
        if (expression.isBlank()) return 0.0
        val tokens = expression.split("+")
        var sum = 0.0
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.isNotEmpty()) {
                sum += trimmed.toDoubleOrNull() ?: 0.0
            }
        }
        return sum
    }

    private fun calculateSettlements(
        members: List<String>,
        spendings: Map<String, Double>
    ): List<String> {
        val n = members.size
        val totalSpent = spendings.values.sum()
        val fairShare = totalSpent / n

        val debtors = mutableListOf<Pair<String, Double>>()
        val creditors = mutableListOf<Pair<String, Double>>()

        for (member in members) {
            val paid = spendings[member] ?: 0.0
            val balance = ((paid - fairShare) * 100).roundToInt() / 100.0

            if (balance < -0.01) {
                debtors.add(Pair(member, -balance))
            } else if (balance > 0.01) {
                creditors.add(Pair(member, balance))
            }
        }

        debtors.sortByDescending { it.second }
        creditors.sortByDescending { it.second }

        val settlements = mutableListOf<String>()
        var d = 0
        var c = 0

        val dList = debtors.map { it.first to it.second }.toMutableList()
        val cList = creditors.map { it.first to it.second }.toMutableList()

        while (d < dList.size && c < cList.size) {
            val debtorName = dList[d].first
            val debtorOwes = dList[d].second

            val creditorName = cList[c].first
            val creditorGets = cList[c].second

            val settled = min(debtorOwes, creditorGets)
            settlements.add("$debtorName owes $creditorName: ₹%.2f".format(settled))

            val remainingDebt = debtorOwes - settled
            val remainingCredit = creditorGets - settled

            dList[d] = debtorName to remainingDebt
            cList[c] = creditorName to remainingCredit

            if (remainingDebt <= 0.01) d++
            if (remainingCredit <= 0.01) c++
        }

        return if (settlements.isEmpty()) listOf("All settled up! Nobody owes anything.") else settlements
    }

    inner class SingleScreenAdapter : BaseAdapter() {
        override fun getCount(): Int = memberList.size
        override fun getItem(position: Int): Any = memberList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.item_member_spending, parent, false)

            val tvName = view.findViewById<TextView>(R.id.tvMemberName)
            val etAmount = view.findViewById<EditText>(R.id.etMemberAmount)
            val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteMember)

            val memberName = memberList[position]
            tvName.text = "${position + 1}. $memberName"

            etAmount.tag = memberName
            etAmount.setText(rawInputMap[memberName] ?: "")

            etAmount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (etAmount.tag == memberName) {
                        val input = s.toString()
                        rawInputMap[memberName] = input
                        spendingMap[memberName] = parseSum(input)
                        updateLiveSummary()
                    }
                }
            })

            btnDelete.setOnClickListener {
                memberList.removeAt(position)
                rawInputMap.remove(memberName)
                spendingMap.remove(memberName)
                notifyDataSetChanged()
                updateLiveSummary()
            }

            return view
        }
    }
}