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
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.min
import kotlin.math.roundToInt

class ExpenseActivity : AppCompatActivity() {

    private var tripSessionId: Long = 0L
    private val rawInputMap = mutableMapOf<String, String>()
    private val spendingMap = mutableMapOf<String, Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense)

        tripSessionId = System.currentTimeMillis()

        val membersList = intent.getStringArrayListExtra("MEMBERS_LIST") ?: arrayListOf()

        for (member in membersList) {
            rawInputMap[member] = ""
            spendingMap[member] = 0.0
        }

        val btnExpenseBack = findViewById<ImageButton>(R.id.btnExpenseBack)
        val etTripTitle = findViewById<EditText>(R.id.etTripTitle)
        val lvSpendingList = findViewById<ListView>(R.id.lvSpendingList)
        val btnCalculateSplit = findViewById<Button>(R.id.btnCalculateSplit)

        btnExpenseBack.setOnClickListener {
            finish()
        }

        val adapter = SpendingAdapter(membersList)
        lvSpendingList.adapter = adapter

        btnCalculateSplit.setOnClickListener {
            val totalSpent = spendingMap.values.sum()
            if (totalSpent <= 0.0) {
                Toast.makeText(this, "Please enter at least one spending amount!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val settlements = calculateSettlements(membersList, spendingMap)
            val title = etTripTitle.text.toString().trim()

            val intent = Intent(this, SettlementActivity::class.java).apply {
                putExtra("TRIP_ID", tripSessionId)
                putExtra("TRIP_TITLE", if (title.isEmpty()) "Trip Expense" else title)
                putExtra("TOTAL_AMOUNT", totalSpent)
                putExtra("MEMBER_COUNT", membersList.size)
                putStringArrayListExtra("SETTLEMENTS", ArrayList(settlements))
            }
            startActivity(intent)
        }
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

    inner class SpendingAdapter(
        private val members: List<String>
    ) : BaseAdapter() {

        override fun getCount(): Int = members.size
        override fun getItem(position: Int): Any = members[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@ExpenseActivity)
                .inflate(R.layout.item_member_spending, parent, false)

            val tvName = view.findViewById<TextView>(R.id.tvMemberName)
            val etAmount = view.findViewById<EditText>(R.id.etMemberAmount)

            val memberName = members[position]
            // Show serial number directly in front of the name
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
                    }
                }
            })

            return view
        }
    }
}