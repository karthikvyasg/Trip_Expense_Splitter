package com.example.tripexpensesplitter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
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

    private val memberList = ArrayList<String>()
    private val expenseList = ArrayList<UnequalExpenseItem>()

    private lateinit var etTripTitle: EditText
    private lateinit var etMemberName: EditText
    private lateinit var tvMembersSummary: TextView
    private lateinit var tvEmptyPrompt: TextView
    private lateinit var tvLiveTotal: TextView
    private lateinit var layoutContainer: LinearLayout

    private var tripSessionId: Long = System.currentTimeMillis()
    private var activeToast: Toast? = null

    // Google Play In-App Update Manager
    private lateinit var appUpdateManager: AppUpdateManager
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // Update was cancelled or failed - re-trigger since it is mandatory
            checkForMandatoryUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        DisplayUtils.enableMaxRefreshRate(this)
        setContentView(R.layout.activity_unequal_split)

        // Initialize update check
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForMandatoryUpdate()

        etTripTitle = findViewById(R.id.etUnequalTripTitle)
        etMemberName = findViewById(R.id.etUnequalMemberName)
        tvMembersSummary = findViewById(R.id.tvUnequalMembersSummary)
        tvEmptyPrompt = findViewById(R.id.tvEmptyExpensePrompt)
        tvLiveTotal = findViewById(R.id.tvUnequalLiveTotal)
        layoutContainer = findViewById(R.id.layoutExpenseItemsContainer)

        val btnBack = findViewById<ImageButton>(R.id.btnBackUnequal)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenuUnequal)
        val btnAddMember = findViewById<Button>(R.id.btnAddUnequalMember)
        val btnOpenAddExpense = findViewById<Button>(R.id.btnOpenAddExpenseDialog)
        val btnCalculate = findViewById<Button>(R.id.btnCalculateUnequalSplit)
        val btnHistory = findViewById<Button>(R.id.btnUnequalHistory)
        val btnReset = findViewById<Button>(R.id.btnUnequalReset)

        btnBack.setOnClickListener { finish() }

        // Hamburger Menu trigger (matching MainActivity)
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
                .setMessage("Are you sure you want to clear this trip? All added members and items will be lost.")
                .setPositiveButton("Reset") { _, _ ->
                    tripSessionId = System.currentTimeMillis()
                    etTripTitle.text.clear()
                    etMemberName.text.clear()
                    memberList.clear()
                    expenseList.clear()
                    updateMembersDisplay()
                    renderExpenseList()
                    showSingleToast("Trip reset")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        btnAddMember.setOnClickListener {
            val name = etMemberName.text.toString().trim()
            if (name.isEmpty()) {
                showSingleToast("Please enter a member name")
                return@setOnClickListener
            }
            if (memberList.contains(name)) {
                showSingleToast("Member already exists")
                return@setOnClickListener
            }
            memberList.add(name)
            etMemberName.text.clear()
            updateMembersDisplay()
        }

        btnOpenAddExpense.setOnClickListener {
            if (memberList.size < 2) {
                showSingleToast("Add at least 2 members first!")
                return@setOnClickListener
            }
            showAddExpenseItemDialog()
        }

        btnCalculate.setOnClickListener {
            if (memberList.size < 2) {
                showSingleToast("Add at least 2 members to split!")
                return@setOnClickListener
            }
            if (expenseList.isEmpty()) {
                showSingleToast("Add at least one place/activity expense!")
                return@setOnClickListener
            }

            val fairShareMap = mutableMapOf<String, Double>()
            val placesJoinedMap = mutableMapOf<String, Int>()
            for (m in memberList) {
                fairShareMap[m] = 0.0
                placesJoinedMap[m] = 0
            }

            for (expense in expenseList) {
                val participants = expense.participants
                if (participants.isNotEmpty()) {
                    val splitAmount = expense.amount / participants.size
                    for (p in participants) {
                        fairShareMap[p] = (fairShareMap[p] ?: 0.0) + splitAmount
                        placesJoinedMap[p] = (placesJoinedMap[p] ?: 0) + 1
                    }
                }
            }

            val totalPlaces = expenseList.size
            val breakdownList = ArrayList<String>()
            for (m in memberList) {
                val share = fairShareMap[m] ?: 0.0
                val joined = placesJoinedMap[m] ?: 0
                val note = if (joined == totalPlaces) {
                    "joined all $totalPlaces places"
                } else {
                    "joined $joined of $totalPlaces places"
                }
                breakdownList.add("$m: ₹%.2f ($note)".format(share))
            }

            val settlements = calculateUnequalSettlements(memberList, expenseList)
            val totalTripCost = expenseList.sumOf { it.amount }
            val rawTitle = etTripTitle.text.toString().trim()

            // Serialize expenses to JSON for history reloading
            val jsonArray = JSONArray()
            for (item in expenseList) {
                val obj = JSONObject()
                obj.put("title", item.title)
                obj.put("amount", item.amount)
                obj.put("paidBy", item.paidBy)
                val partArray = JSONArray()
                item.participants.forEach { partArray.put(it) }
                obj.put("participants", partArray)
                jsonArray.put(obj)
            }

            val intent = Intent(this, SettlementActivity::class.java).apply {
                putExtra("IS_UNEQUAL_SPLIT", true)
                putExtra("TRIP_ID", tripSessionId)
                putExtra("TRIP_TITLE", if (rawTitle.isEmpty()) "Unequal Trip Split" else rawTitle)
                putExtra("TOTAL_AMOUNT", totalTripCost)
                putExtra("MEMBER_COUNT", memberList.size)
                putStringArrayListExtra("SETTLEMENTS", ArrayList(settlements))
                putStringArrayListExtra("INDIVIDUAL_BREAKDOWN", breakdownList)
                putStringArrayListExtra("MEMBERS", memberList)
                putExtra("UNEQUAL_ITEMS_JSON", jsonArray.toString())
            }
            startActivity(intent)
        }

        // Handle reloading from History on fresh start
        handleHistoryIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // If an update was in progress and the app returns to foreground, ensure it resumes
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleHistoryIntent(intent)
    }

    private fun handleHistoryIntent(inIntent: Intent) {
        if (inIntent.getBooleanExtra("LOAD_UNEQUAL_FOR_EDIT", false)) {
            tripSessionId = inIntent.getLongExtra("TRIP_ID", System.currentTimeMillis())
            val savedTitle = inIntent.getStringExtra("TRIP_TITLE") ?: ""
            etTripTitle.setText(savedTitle)

            val savedMembers = inIntent.getStringArrayListExtra("MEMBERS") ?: arrayListOf()
            memberList.clear()
            memberList.addAll(savedMembers)

            val rawJson = inIntent.getStringExtra("UNEQUAL_ITEMS_JSON") ?: ""
            expenseList.clear()
            if (rawJson.isNotEmpty()) {
                try {
                    val array = JSONArray(rawJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val title = obj.getString("title")
                        val amount = obj.getDouble("amount")
                        val paidBy = obj.getString("paidBy")

                        val pArray = obj.getJSONArray("participants")
                        val participants = mutableListOf<String>()
                        for (j in 0 until pArray.length()) {
                            participants.add(pArray.getString(j))
                        }

                        expenseList.add(
                            UnequalExpenseItem(
                                title = title,
                                amount = amount,
                                paidBy = paidBy,
                                participants = participants
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            updateMembersDisplay()
            renderExpenseList()
            showSingleToast("Unequal trip loaded for editing")
        } else {
            updateMembersDisplay()
            renderExpenseList()
        }
    }

    // Synced menu matching MainActivity exactly
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

    private fun updateMembersDisplay() {
        if (memberList.isEmpty()) {
            tvMembersSummary.text = "No members added yet. Add at least 2 members."
        } else {
            tvMembersSummary.text = "Trip Members (${memberList.size}): " + memberList.joinToString(", ")
        }
    }

    private fun showAddExpenseItemDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_unequal_item, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etItemTitle)
        val etAmount = dialogView.findViewById<EditText>(R.id.etItemAmount)
        val spPaidBy = dialogView.findViewById<Spinner>(R.id.spPaidBy)
        val cbContainer = dialogView.findViewById<LinearLayout>(R.id.layoutParticipantCheckboxes)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelItem)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAddItem)

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, memberList)
        spPaidBy.adapter = spinnerAdapter

        val checkBoxes = mutableListOf<CheckBox>()
        for (member in memberList) {
            val cb = CheckBox(this).apply {
                text = member
                isChecked = true
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#212529"))
            }
            checkBoxes.add(cb)
            cbContainer.addView(cb)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnAdd.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()
            val amount = parseSum(amountStr)

            if (title.isEmpty()) {
                showSingleToast("Please enter an activity/place name")
                return@setOnClickListener
            }
            if (amount <= 0.0) {
                showSingleToast("Please enter a valid amount")
                return@setOnClickListener
            }

            val selectedParticipants = checkBoxes.filter { it.isChecked }.map { it.text.toString() }
            if (selectedParticipants.isEmpty()) {
                showSingleToast("Select at least one member who participated!")
                return@setOnClickListener
            }

            val paidBy = spPaidBy.selectedItem.toString()

            val item = UnequalExpenseItem(
                title = title,
                amount = amount,
                paidBy = paidBy,
                participants = selectedParticipants
            )

            expenseList.add(item)
            renderExpenseList()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        dialog.show()

        val width = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun renderExpenseList() {
        layoutContainer.removeAllViews()

        if (expenseList.isEmpty()) {
            tvEmptyPrompt.visibility = View.VISIBLE
            tvLiveTotal.text = "₹0.00"
            return
        }

        tvEmptyPrompt.visibility = View.GONE
        val total = expenseList.sumOf { it.amount }
        tvLiveTotal.text = "₹%.2f".format(total)

        for ((index, item) in expenseList.withIndex()) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_unequal_expense, layoutContainer, false)

            val tvTitle = itemView.findViewById<TextView>(R.id.tvExpenseTitle)
            val tvAmount = itemView.findViewById<TextView>(R.id.tvExpenseAmount)
            val tvPaidBy = itemView.findViewById<TextView>(R.id.tvPaidByInfo)
            val tvParticipants = itemView.findViewById<TextView>(R.id.tvParticipantsInfo)
            val btnDelete = itemView.findViewById<ImageButton>(R.id.btnDeleteExpense)

            tvTitle.text = "${index + 1}. ${item.title}"
            tvAmount.text = "₹%.2f".format(item.amount)
            tvPaidBy.text = "Paid by: ${item.paidBy}"

            val participantText = if (item.participants.size == memberList.size) {
                "Split equally among all ${memberList.size} members"
            } else {
                "Split among ${item.participants.size} of ${memberList.size}: " + item.participants.joinToString(", ")
            }
            tvParticipants.text = participantText

            btnDelete.setOnClickListener {
                expenseList.removeAt(index)
                renderExpenseList()
            }

            layoutContainer.addView(itemView)
        }
    }

    private fun calculateUnequalSettlements(
        members: List<String>,
        expenses: List<UnequalExpenseItem>
    ): List<String> {
        val netBalanceMap = mutableMapOf<String, Double>()
        for (m in members) {
            netBalanceMap[m] = 0.0
        }

        for (expense in expenses) {
            val amount = expense.amount
            val payer = expense.paidBy
            val participants = expense.participants
            val count = participants.size

            if (count > 0) {
                val perPersonShare = amount / count
                netBalanceMap[payer] = (netBalanceMap[payer] ?: 0.0) + amount

                for (participant in participants) {
                    netBalanceMap[participant] = (netBalanceMap[participant] ?: 0.0) - perPersonShare
                }
            }
        }

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

    private fun parseSum(expression: String): Double {
        if (expression.isBlank()) return 0.0
        val cleaned = expression.replace(" ", "")
        val regex = Regex("(?=[+-])")
        val tokens = cleaned.split(regex).filter { it.isNotBlank() }
        var sum = 0.0
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed == "+" || trimmed == "-") continue
            sum += trimmed.toDoubleOrNull() ?: 0.0
        }
        return maxOf(0.0, sum)
    }

    private fun showSingleToast(message: String) {
        activeToast?.cancel()
        activeToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        activeToast?.show()
    }
}