package com.example.tripexpensesplitter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

class UnequalSplitActivity : AppCompatActivity() {

    enum class SplitScenario(val id: Int, val shortLabel: String, val fullTitle: String) {
        SCENARIO_1(1, "SCENARIO: 1 ˅", "Scenario 1: Split by Who Joined & Who Paid How Much"),
        SCENARIO_2(2, "SCENARIO: 2 ˅", "Scenario 2: Pay by Who Consumed How Much")
    }

    private var currentScenario: SplitScenario = SplitScenario.SCENARIO_1

    // Master list of member names across all tables
    private val masterMemberList = ArrayList<String>()

    private class ActivityCardHolder(
        val cardView: View,
        val etTitle: EditText,
        val tvHeaderActive: TextView,
        val tvHeaderConsumed: TextView,
        val rowsContainer: LinearLayout,
        val tvEmptyHint: TextView,
        val tvSummary: TextView,
        val tvTotal: TextView,
        val rowViews: ArrayList<MemberRowHolder> = ArrayList()
    )

    private class MemberRowHolder(
        val rowView: View,
        val tvIndex: TextView,
        val etName: EditText,
        val cbActive: CheckBox,
        val etConsumed: EditText,
        val etPaid: EditText,
        val btnDelete: ImageButton
    )

    private val activityCardHolders = ArrayList<ActivityCardHolder>()

    private lateinit var etTripTitle: EditText
    private lateinit var layoutContainer: LinearLayout
    private lateinit var tvLiveGrandTotal: TextView
    private lateinit var btnScenario: Button

    private var tripSessionId: Long = System.currentTimeMillis()
    private var activeToast: Toast? = null

    private lateinit var appUpdateManager: AppUpdateManager
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            checkForMandatoryUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        DisplayUtils.enableMaxRefreshRate(this)
        setContentView(R.layout.activity_unequal_split)

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForMandatoryUpdate()

        etTripTitle = findViewById(R.id.etUnequalTripTitle)
        layoutContainer = findViewById(R.id.layoutActivitiesContainer)
        tvLiveGrandTotal = findViewById(R.id.tvUnequalLiveTotal)
        btnScenario = findViewById(R.id.btnScenarioDropdown)

        val btnBack = findViewById<ImageButton>(R.id.btnBackUnequal)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenuUnequal)
        val btnAddActivity = findViewById<Button>(R.id.btnAddNewExpenseActivity)
        val btnCalculate = findViewById<Button>(R.id.btnCalculateUnequalSplit)
        val btnHistory = findViewById<Button>(R.id.btnUnequalHistory)
        val btnReset = findViewById<Button>(R.id.btnUnequalReset)

        btnBack.setOnClickListener { finish() }

        btnMenu?.setOnClickListener { view ->
            showAppMenu(view)
        }

        btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        btnReset.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Trip?")
                .setMessage("Clear all activity tables and member inputs?")
                .setPositiveButton("Reset") { _, _ ->
                    tripSessionId = System.currentTimeMillis()
                    etTripTitle.text.clear()
                    masterMemberList.clear()
                    activityCardHolders.clear()
                    layoutContainer.removeAllViews()
                    initFreshState()
                    showSingleToast("Trip reset")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnScenario.setOnClickListener { anchorView ->
            showScenarioDropdown(anchorView)
        }

        btnAddActivity.setOnClickListener {
            addNewActivityCard()
        }

        btnCalculate.setOnClickListener {
            processCalculationAndNavigate()
        }

        // Initialize state or load from history
        if (!handleHistoryIntent(intent)) {
            initFreshState()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleHistoryIntent(intent)
    }

    private fun showScenarioDropdown(anchorView: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_scenario_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            isOutsideTouchable = true
        }

        val dot1 = popupView.findViewById<View>(R.id.dotScenario1)
        val dot2 = popupView.findViewById<View>(R.id.dotScenario2)

        dot1?.visibility = if (currentScenario == SplitScenario.SCENARIO_1) View.VISIBLE else View.INVISIBLE
        dot2?.visibility = if (currentScenario == SplitScenario.SCENARIO_2) View.VISIBLE else View.INVISIBLE

        popupView.findViewById<View>(R.id.itemScenario1)?.setOnClickListener {
            switchScenario(SplitScenario.SCENARIO_1)
            popupWindow.dismiss()
        }

        popupView.findViewById<View>(R.id.itemScenario2)?.setOnClickListener {
            switchScenario(SplitScenario.SCENARIO_2)
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(anchorView, 0, 8)
    }

    private fun switchScenario(newScenario: SplitScenario) {
        if (currentScenario == newScenario) return
        currentScenario = newScenario
        btnScenario.text = newScenario.shortLabel

        for (card in activityCardHolders) {
            applyScenarioVisibilityToCard(card)
            updateCardSummaryAndTotal(card)
        }
        updateGrandTotal()
        showSingleToast("Switched to ${newScenario.fullTitle}")
    }

    private fun applyScenarioVisibilityToCard(cardHolder: ActivityCardHolder) {
        val isScenario1 = currentScenario == SplitScenario.SCENARIO_1

        cardHolder.tvHeaderActive.visibility = if (isScenario1) View.VISIBLE else View.GONE
        cardHolder.tvHeaderConsumed.visibility = if (isScenario1) View.GONE else View.VISIBLE

        for (row in cardHolder.rowViews) {
            row.cbActive.visibility = if (isScenario1) View.VISIBLE else View.GONE
            row.etConsumed.visibility = if (isScenario1) View.GONE else View.VISIBLE
        }
    }

    private fun initFreshState() {
        masterMemberList.clear()
        addNewActivityCard("Expense Activity 1")
    }

    private fun addNewActivityCard(customTitle: String? = null) {
        val cardIndex = activityCardHolders.size + 1
        val cardView = LayoutInflater.from(this).inflate(R.layout.item_activity_table_card, layoutContainer, false)

        val etTitle = cardView.findViewById<EditText>(R.id.etActivityTitle)
        val tvHeaderActive = cardView.findViewById<TextView>(R.id.tvHeaderActive)
        val tvHeaderConsumed = cardView.findViewById<TextView>(R.id.tvHeaderConsumed)
        val rowsContainer = cardView.findViewById<LinearLayout>(R.id.layoutPersonRowsContainer)
        val tvEmptyHint = cardView.findViewById<TextView>(R.id.tvEmptyPersonHint)
        val btnAddPerson = cardView.findViewById<Button>(R.id.btnAddPersonRow)
        val btnDeleteCard = cardView.findViewById<ImageButton>(R.id.btnDeleteActivityCard)
        val tvSummary = cardView.findViewById<TextView>(R.id.tvActivitySummary)
        val tvTotal = cardView.findViewById<TextView>(R.id.tvActivityCardTotal)

        etTitle.setText(customTitle ?: "Expense Activity $cardIndex")

        val cardHolder = ActivityCardHolder(
            cardView = cardView,
            etTitle = etTitle,
            tvHeaderActive = tvHeaderActive,
            tvHeaderConsumed = tvHeaderConsumed,
            rowsContainer = rowsContainer,
            tvEmptyHint = tvEmptyHint,
            tvSummary = tvSummary,
            tvTotal = tvTotal
        )

        for (member in masterMemberList) {
            addMemberRowToCard(cardHolder, member)
        }

        applyScenarioVisibilityToCard(cardHolder)

        btnAddPerson.setOnClickListener {
            addNewMemberGlobally(originCard = cardHolder)
        }

        btnDeleteCard.setOnClickListener {
            if (activityCardHolders.size <= 1) {
                showSingleToast("At least one expense activity is required!")
                return@setOnClickListener
            }
            layoutContainer.removeView(cardView)
            activityCardHolders.remove(cardHolder)
            updateGrandTotal()
        }

        activityCardHolders.add(cardHolder)
        layoutContainer.addView(cardView)
        updateCardSummaryAndTotal(cardHolder)
        updateGrandTotal()
    }

    private fun addNewMemberGlobally(originCard: ActivityCardHolder? = null) {
        val newPersonNum = masterMemberList.size + 1
        val newMemberName = "Person $newPersonNum"
        masterMemberList.add(newMemberName)

        for (card in activityCardHolders) {
            val isActiveOnThisCard = if (originCard != null) card == originCard else true
            addMemberRowToCard(
                cardHolder = card,
                memberName = newMemberName,
                initialPaid = 0.0,
                initialConsumed = 0.0,
                initialActive = isActiveOnThisCard
            )
            applyScenarioVisibilityToCard(card)
            updateCardSummaryAndTotal(card)
        }
        updateGrandTotal()
    }

    private fun addMemberRowToCard(
        cardHolder: ActivityCardHolder,
        memberName: String,
        initialPaid: Double = 0.0,
        initialConsumed: Double = 0.0,
        initialActive: Boolean = true
    ) {
        val rowView = LayoutInflater.from(this).inflate(R.layout.item_activity_person_row, cardHolder.rowsContainer, false)
        val tvIndex = rowView.findViewById<TextView>(R.id.tvPersonIndex)
        val etName = rowView.findViewById<EditText>(R.id.etPersonName)
        val cbActive = rowView.findViewById<CheckBox>(R.id.cbPersonActive)
        val etConsumed = rowView.findViewById<EditText>(R.id.etPersonConsumed)
        val etPaid = rowView.findViewById<EditText>(R.id.etPersonPaid)
        val btnDelete = rowView.findViewById<ImageButton>(R.id.btnDeletePerson)

        val rowIndex = cardHolder.rowViews.size
        tvIndex.text = "${rowIndex + 1}."
        etName.setText(memberName)
        cbActive.isChecked = initialActive

        val isScenario1 = currentScenario == SplitScenario.SCENARIO_1
        cbActive.visibility = if (isScenario1) View.VISIBLE else View.GONE
        etConsumed.visibility = if (isScenario1) View.GONE else View.VISIBLE

        if (initialPaid > 0.0) {
            etPaid.setText(if (initialPaid % 1.0 == 0.0) initialPaid.toLong().toString() else "%.2f".format(initialPaid))
        }

        if (initialConsumed > 0.0) {
            etConsumed.setText(if (initialConsumed % 1.0 == 0.0) initialConsumed.toLong().toString() else "%.2f".format(initialConsumed))
        }

        // Two-way sync: changing person name in one table updates all other tables
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentPos = cardHolder.rowViews.indexOfFirst { it.rowView == rowView }
                if (currentPos in masterMemberList.indices) {
                    val updatedName = s.toString().trim()
                    masterMemberList[currentPos] = updatedName
                    for (otherCard in activityCardHolders) {
                        if (otherCard != cardHolder && currentPos in otherCard.rowViews.indices) {
                            val otherEt = otherCard.rowViews[currentPos].etName
                            if (otherEt.text.toString() != updatedName) {
                                otherEt.setText(updatedName)
                            }
                        }
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val calcWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCardSummaryAndTotal(cardHolder)
                updateGrandTotal()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etPaid.addTextChangedListener(calcWatcher)
        etConsumed.addTextChangedListener(calcWatcher)
        cbActive.setOnCheckedChangeListener { _, _ ->
            updateCardSummaryAndTotal(cardHolder)
            updateGrandTotal()
        }

        btnDelete.setOnClickListener {
            val positionToDelete = cardHolder.rowViews.indexOfFirst { it.rowView == rowView }
            if (positionToDelete != -1) {
                deleteMemberGlobally(positionToDelete)
            }
        }

        val memberHolder = MemberRowHolder(rowView, tvIndex, etName, cbActive, etConsumed, etPaid, btnDelete)
        cardHolder.rowViews.add(memberHolder)
        cardHolder.rowsContainer.addView(rowView)
        cardHolder.tvEmptyHint.visibility = View.GONE
    }

    private fun deleteMemberGlobally(index: Int) {
        if (index !in masterMemberList.indices) return
        masterMemberList.removeAt(index)

        for (card in activityCardHolders) {
            if (index in card.rowViews.indices) {
                val holder = card.rowViews.removeAt(index)
                card.rowsContainer.removeView(holder.rowView)

                // Re-index remaining rows: 1., 2., 3.
                for ((i, row) in card.rowViews.withIndex()) {
                    row.tvIndex.text = "${i + 1}."
                }

                if (card.rowViews.isEmpty()) {
                    card.tvEmptyHint.visibility = View.VISIBLE
                }
                updateCardSummaryAndTotal(card)
            }
        }
        updateGrandTotal()
    }

    private fun updateCardSummaryAndTotal(cardHolder: ActivityCardHolder) {
        var cardPaidTotal = 0.0

        if (currentScenario == SplitScenario.SCENARIO_1) {
            var activeCount = 0
            for (row in cardHolder.rowViews) {
                val paid = row.etPaid.text.toString().toDoubleOrNull() ?: 0.0
                cardPaidTotal += paid
                if (row.cbActive.isChecked) {
                    activeCount++
                }
            }
            val splitShare = if (activeCount > 0) cardPaidTotal / activeCount else 0.0
            cardHolder.tvSummary.text = "$activeCount of ${cardHolder.rowViews.size} active (₹%.2f/person)".format(splitShare)
            cardHolder.tvTotal.text = "Activity Total: ₹%.2f".format(cardPaidTotal)
        } else {
            var cardConsumedTotal = 0.0
            for (row in cardHolder.rowViews) {
                val paid = row.etPaid.text.toString().toDoubleOrNull() ?: 0.0
                val consumed = row.etConsumed.text.toString().toDoubleOrNull() ?: 0.0
                cardPaidTotal += paid
                cardConsumedTotal += consumed
            }
            val diff = cardPaidTotal - cardConsumedTotal
            val diffText = if (kotlin.math.abs(diff) > 0.01) {
                if (diff > 0) " (₹%.2f unallocated)".format(diff) else " (₹%.2f over-consumed)".format(-diff)
            } else ""

            cardHolder.tvSummary.text = "Used: ₹%.2f%s".format(cardConsumedTotal, diffText)
            cardHolder.tvTotal.text = "Paid Total: ₹%.2f".format(cardPaidTotal)
        }
    }

    private fun updateGrandTotal() {
        var grandTotal = 0.0
        for (card in activityCardHolders) {
            for (row in card.rowViews) {
                grandTotal += row.etPaid.text.toString().toDoubleOrNull() ?: 0.0
            }
        }
        tvLiveGrandTotal.text = "₹%.2f".format(grandTotal)
    }

    private fun processCalculationAndNavigate() {
        val validMembers = masterMemberList.mapIndexed { idx, name ->
            name.ifBlank { "Person ${idx + 1}" }
        }

        if (validMembers.size < 2) {
            showSingleToast("Add at least 2 people to calculate split!")
            return
        }

        val netBalanceMap = mutableMapOf<String, Double>()
        val individualConsumptionMap = mutableMapOf<String, Double>()
        for (m in validMembers) {
            netBalanceMap[m] = 0.0
            individualConsumptionMap[m] = 0.0
        }

        var grandTotal = 0.0
        val structuredJsonArray = JSONArray()

        for (card in activityCardHolders) {
            val activityTitle = card.etTitle.text.toString().trim().ifEmpty { "Expense Activity" }
            var cardPaidTotal = 0.0
            var cardConsumedTotal = 0.0
            val activeMembers = mutableListOf<String>()
            val paidByMap = mutableMapOf<String, Double>()
            val consumedByMap = mutableMapOf<String, Double>()

            for ((idx, row) in card.rowViews.withIndex()) {
                val currentMemberName = validMembers.getOrElse(idx) { "Person ${idx + 1}" }
                val paid = row.etPaid.text.toString().toDoubleOrNull() ?: 0.0
                val consumed = row.etConsumed.text.toString().toDoubleOrNull() ?: 0.0

                cardPaidTotal += paid
                cardConsumedTotal += consumed

                if (paid > 0.0) {
                    paidByMap[currentMemberName] = paid
                    netBalanceMap[currentMemberName] = (netBalanceMap[currentMemberName] ?: 0.0) + paid
                }

                if (consumed > 0.0) {
                    consumedByMap[currentMemberName] = consumed
                }

                if (row.cbActive.isChecked) {
                    activeMembers.add(currentMemberName)
                }
            }

            grandTotal += cardPaidTotal

            if (currentScenario == SplitScenario.SCENARIO_1) {
                if (activeMembers.isNotEmpty() && cardPaidTotal > 0.0) {
                    val perPersonShare = cardPaidTotal / activeMembers.size
                    for (activeMember in activeMembers) {
                        netBalanceMap[activeMember] = (netBalanceMap[activeMember] ?: 0.0) - perPersonShare
                        individualConsumptionMap[activeMember] = (individualConsumptionMap[activeMember] ?: 0.0) + perPersonShare
                    }
                }
            } else {
                // Scenario 2: Deduct actual consumption from each person
                for ((member, consumedAmt) in consumedByMap) {
                    netBalanceMap[member] = (netBalanceMap[member] ?: 0.0) - consumedAmt
                    individualConsumptionMap[member] = (individualConsumptionMap[member] ?: 0.0) + consumedAmt
                }

                // If paid total exceeds itemized consumption (e.g. shared tax, tip), split remainder among users who consumed
                val unallocated = cardPaidTotal - cardConsumedTotal
                if (unallocated > 0.01) {
                    val sharedPool = if (consumedByMap.isNotEmpty()) consumedByMap.keys.toList() else validMembers
                    val extraPerPerson = unallocated / sharedPool.size
                    for (member in sharedPool) {
                        netBalanceMap[member] = (netBalanceMap[member] ?: 0.0) - extraPerPerson
                        individualConsumptionMap[member] = (individualConsumptionMap[member] ?: 0.0) + extraPerPerson
                    }
                }
            }

            val cardObj = JSONObject()
            cardObj.put("title", activityTitle)
            cardObj.put("totalAmount", cardPaidTotal)

            val paidObj = JSONObject()
            paidByMap.forEach { (k, v) -> paidObj.put(k, v) }
            cardObj.put("paidByMap", paidObj)

            val consumedObj = JSONObject()
            consumedByMap.forEach { (k, v) -> consumedObj.put(k, v) }
            cardObj.put("consumedByMap", consumedObj)

            val partArray = JSONArray()
            activeMembers.forEach { partArray.put(it) }
            cardObj.put("participants", partArray)

            structuredJsonArray.put(cardObj)
        }

        if (grandTotal <= 0.0) {
            showSingleToast("Enter at least one paid expense amount!")
            return
        }

        val breakdownList = ArrayList<String>()
        for (m in validMembers) {
            val consumed = individualConsumptionMap[m] ?: 0.0
            breakdownList.add("$m consumed: ₹%.2f".format(consumed))
        }

        val settlements = solveDebts(netBalanceMap)
        val rawTitle = etTripTitle.text.toString().trim()

        val intent = Intent(this, SettlementActivity::class.java).apply {
            putExtra("IS_UNEQUAL_SPLIT", true)
            putExtra("TRIP_ID", tripSessionId)
            putExtra("TRIP_TITLE", if (rawTitle.isEmpty()) currentScenario.fullTitle else rawTitle)
            putExtra("TOTAL_AMOUNT", grandTotal)
            putExtra("MEMBER_COUNT", validMembers.size)
            putStringArrayListExtra("SETTLEMENTS", ArrayList(settlements))
            putStringArrayListExtra("INDIVIDUAL_BREAKDOWN", breakdownList)
            putStringArrayListExtra("MEMBERS", ArrayList(validMembers))
            putExtra("UNEQUAL_ITEMS_JSON", structuredJsonArray.toString())
            putExtra("SCENARIO_ID", currentScenario.id)
        }
        startActivity(intent)
    }

    private fun solveDebts(netBalanceMap: Map<String, Double>): List<String> {
        val debtors = mutableListOf<Pair<String, Double>>()
        val creditors = mutableListOf<Pair<String, Double>>()

        for ((member, rawBalance) in netBalanceMap) {
            val balance = ((rawBalance) * 100).roundToInt() / 100.0
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

    private fun handleHistoryIntent(inIntent: Intent): Boolean {
        if (!inIntent.getBooleanExtra("LOAD_UNEQUAL_FOR_EDIT", false)) {
            return false
        }

        // 1. Completely clear existing in-memory state and UI before rebuilding
        activityCardHolders.clear()
        masterMemberList.clear()
        layoutContainer.removeAllViews()

        // 2. Set Session & Trip Title
        tripSessionId = inIntent.getLongExtra("TRIP_ID", System.currentTimeMillis())
        val savedTitle = inIntent.getStringExtra("TRIP_TITLE") ?: ""
        etTripTitle.setText(savedTitle)

        // 3. Resolve the exact Scenario ID saved with this record
        val scenarioId = inIntent.getIntExtra("SCENARIO_ID", 1)
        val targetScenario = SplitScenario.values().firstOrNull { it.id == scenarioId } ?: SplitScenario.SCENARIO_1

        // Explicitly set the target scenario on the variable and dropdown label
        currentScenario = targetScenario
        btnScenario.text = targetScenario.shortLabel

        // 4. Restore Member Roster
        val savedMembers = inIntent.getStringArrayListExtra("MEMBERS") ?: arrayListOf()
        masterMemberList.addAll(savedMembers)

        val rawJson = inIntent.getStringExtra("UNEQUAL_ITEMS_JSON") ?: ""
        if (rawJson.isNotEmpty()) {
            try {
                val array = JSONArray(rawJson)

                // Fallback: extract member names from JSON if MEMBERS extra wasn't supplied
                if (masterMemberList.isEmpty()) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        if (obj.has("paidByMap")) {
                            val pObj = obj.getJSONObject("paidByMap")
                            val keys = pObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                if (!masterMemberList.contains(k)) masterMemberList.add(k)
                            }
                        }
                        if (obj.has("consumedByMap")) {
                            val cObj = obj.getJSONObject("consumedByMap")
                            val keys = cObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                if (!masterMemberList.contains(k)) masterMemberList.add(k)
                            }
                        }
                        if (obj.has("participants")) {
                            val pArr = obj.getJSONArray("participants")
                            for (j in 0 until pArr.length()) {
                                val p = pArr.getString(j)
                                if (!masterMemberList.contains(p)) masterMemberList.add(p)
                            }
                        }
                    }
                }

                // 5. Reconstruct each activity card
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val title = obj.optString("title", "Expense Activity ${i + 1}")
                    val paidMap = mutableMapOf<String, Double>()
                    val consumedMap = mutableMapOf<String, Double>()

                    if (obj.has("paidByMap")) {
                        val pObj = obj.getJSONObject("paidByMap")
                        val keys = pObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            paidMap[k] = pObj.getDouble(k)
                        }
                    }

                    if (obj.has("consumedByMap")) {
                        val cObj = obj.getJSONObject("consumedByMap")
                        val keys = cObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            consumedMap[k] = cObj.getDouble(k)
                        }
                    }

                    val activeList = mutableListOf<String>()
                    if (obj.has("participants")) {
                        val pArr = obj.getJSONArray("participants")
                        for (j in 0 until pArr.length()) activeList.add(pArr.getString(j))
                    }

                    val cardView = LayoutInflater.from(this).inflate(R.layout.item_activity_table_card, layoutContainer, false)
                    val etTitle = cardView.findViewById<EditText>(R.id.etActivityTitle)
                    val tvHeaderActive = cardView.findViewById<TextView>(R.id.tvHeaderActive)
                    val tvHeaderConsumed = cardView.findViewById<TextView>(R.id.tvHeaderConsumed)
                    val rowsContainer = cardView.findViewById<LinearLayout>(R.id.layoutPersonRowsContainer)
                    val tvEmptyHint = cardView.findViewById<TextView>(R.id.tvEmptyPersonHint)
                    val btnAddPerson = cardView.findViewById<Button>(R.id.btnAddPersonRow)
                    val btnDeleteCard = cardView.findViewById<ImageButton>(R.id.btnDeleteActivityCard)
                    val tvSummary = cardView.findViewById<TextView>(R.id.tvActivitySummary)
                    val tvTotal = cardView.findViewById<TextView>(R.id.tvActivityCardTotal)

                    etTitle.setText(title)
                    val cardHolder = ActivityCardHolder(
                        cardView = cardView,
                        etTitle = etTitle,
                        tvHeaderActive = tvHeaderActive,
                        tvHeaderConsumed = tvHeaderConsumed,
                        rowsContainer = rowsContainer,
                        tvEmptyHint = tvEmptyHint,
                        tvSummary = tvSummary,
                        tvTotal = tvTotal
                    )

                    // Inflate rows with restored values
                    for (m in masterMemberList) {
                        val isAct = if (activeList.isEmpty()) true else activeList.contains(m)
                        val pAmt = paidMap[m] ?: 0.0
                        val cAmt = consumedMap[m] ?: 0.0
                        addMemberRowToCard(
                            cardHolder = cardHolder,
                            memberName = m,
                            initialPaid = pAmt,
                            initialConsumed = cAmt,
                            initialActive = isAct
                        )
                    }

                    // Enforce header and row visibility after all rows are populated
                    applyScenarioVisibilityToCard(cardHolder)

                    btnAddPerson.setOnClickListener { addNewMemberGlobally(originCard = cardHolder) }
                    btnDeleteCard.setOnClickListener {
                        if (activityCardHolders.size <= 1) {
                            showSingleToast("At least one expense activity is required!")
                            return@setOnClickListener
                        }
                        layoutContainer.removeView(cardView)
                        activityCardHolders.remove(cardHolder)
                        updateGrandTotal()
                    }

                    activityCardHolders.add(cardHolder)
                    layoutContainer.addView(cardView)
                    updateCardSummaryAndTotal(cardHolder)
                }

                updateGrandTotal()
                showSingleToast("Loaded ${targetScenario.fullTitle}")
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    private fun checkForMandatoryUpdate() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    private fun showAppMenu(anchorView: View) {
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.layout_custom_menu, null)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
        }

        popupView.findViewById<View>(R.id.menuItemAbout)?.setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }

        popupWindow.showAsDropDown(anchorView, -30, 10)
    }

    private fun showSingleToast(message: String) {
        activeToast?.cancel()
        activeToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        activeToast?.show()
    }
}