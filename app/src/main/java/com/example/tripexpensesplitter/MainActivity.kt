package com.example.tripexpensesplitter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private var tripSessionId: Long = System.currentTimeMillis()
    private val memberList = ArrayList<String>()
    private val rawInputMap = mutableMapOf<String, String>()
    private val spendingMap = mutableMapOf<String, Double>()
    private lateinit var adapter: SingleScreenAdapter

    private lateinit var etTripTitle: EditText
    private lateinit var etMemberName: EditText
    private lateinit var tvLiveTotal: TextView
    private lateinit var tvLivePerPerson: TextView

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
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        DisplayUtils.enableMaxRefreshRate(this)
        setContentView(R.layout.activity_main)

        tripSessionId = System.currentTimeMillis()

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForMandatoryUpdate()

        etTripTitle = findViewById(R.id.etTripTitle)
        etMemberName = findViewById(R.id.etMemberName)
        val btnAddMember = findViewById<Button>(R.id.btnAddMember)
        val lvMembersSpending = findViewById<ListView>(R.id.lvMembersSpending)
        val btnCalculateSplit = findViewById<Button>(R.id.btnCalculateSplit)
        val btnViewHistory = findViewById<Button>(R.id.btnViewHistory)
        val btnResetTrip = findViewById<Button>(R.id.btnResetTrip)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val btnMainBack = findViewById<ImageButton>(R.id.btnMainBack)

        tvLiveTotal = findViewById(R.id.tvLiveTotal)
        tvLivePerPerson = findViewById(R.id.tvLivePerPerson)

        adapter = SingleScreenAdapter()
        lvMembersSpending.adapter = adapter

        btnMenu.setOnClickListener { view ->
            showAppMenu(anchorView = view)
        }

        btnMainBack.setOnClickListener {
            finish()
        }

        tvLiveTotal = findViewById(R.id.tvLiveTotal)
        tvLivePerPerson = findViewById(R.id.tvLivePerPerson)

        adapter = SingleScreenAdapter()
        lvMembersSpending.adapter = adapter

        btnMenu.setOnClickListener { view ->
            showAppMenu(view)
        }

        btnAddMember.setOnClickListener {
            showAddMemberDialog()
        }

        btnCalculateSplit.setOnClickListener {
            if (memberList.size < 2) {
                showSingleToast("Add at least 2 members to split expenses!")
                return@setOnClickListener
            }

            val totalSpent = spendingMap.values.sum()
            if (totalSpent <= 0.0) {
                showSingleToast("Please enter at least one spending amount!")
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
                putStringArrayListExtra("MEMBERS", ArrayList(memberList))
                putStringArrayListExtra("MAP_KEYS", ArrayList(rawInputMap.keys))
                putStringArrayListExtra("MAP_VALUES", ArrayList(rawInputMap.values))

            }
            startActivity(intent)
        }

        btnViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        btnResetTrip.setOnClickListener {
            if (memberList.isEmpty() && etTripTitle.text.isBlank()) {
                showSingleToast("Everything is already cleared")
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

    override fun onResume() {
        super.onResume()
        checkAndLoadTripFromIntent(intent)
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
        }.addOnFailureListener {
            // If Play Services update fails or for local tests, show the blocking fallback dialog
            // You can optionally uncomment this to enforce via dialog:
            // showBlockingUpdateDialog()
        }
    }

    private fun showBlockingUpdateDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Update Required")
            .setMessage("A new version of Trip Expense Splitter is available. Please update to continue using the app.")
            .setCancelable(false) // Non-dismissible
            .setPositiveButton("Update Now") { _, _ ->
                val appPackageName = packageName
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
                }
                finish() // Close current activity until updated
            }
            .show()
    }

    private fun showAddMemberDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_member, null)
        val etDialogName = dialogView.findViewById<EditText>(R.id.dialogEtMemberName)
        val etDialogExpense = dialogView.findViewById<EditText>(R.id.dialogEtMemberExpense)
        val btnCancel = dialogView.findViewById<Button>(R.id.dialogBtnCancel)
        val btnAdd = dialogView.findViewById<Button>(R.id.dialogBtnAdd)

        val prefilledName = etMemberName.text.toString().trim()
        if (prefilledName.isNotEmpty()) {
            etDialogName.setText(prefilledName)
            etDialogName.setSelection(prefilledName.length)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        dialog.window?.apply {
            setGravity(Gravity.TOP)
            attributes = attributes.apply {
                y = 120
            }
        }

        btnAdd.setOnClickListener {
            val name = etDialogName.text.toString().trim()
            val rawExpense = etDialogExpense.text.toString().trim()

            if (name.isEmpty()) {
                showSingleToast("Please enter a name")
                return@setOnClickListener
            }
            if (memberList.contains(name)) {
                showSingleToast("Member already added")
                return@setOnClickListener
            }

            memberList.add(name)
            rawInputMap[name] = rawExpense
            spendingMap[name] = parseSum(rawExpense)

            adapter.notifyDataSetChanged()
            updateLiveSummary()

            etMemberName.text.clear()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.show()
            dialog.dismiss()
        }

        dialog.show()

        val width = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)

        val targetField = if (prefilledName.isNotEmpty()) etDialogExpense else etDialogName
        targetField.requestFocus()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(targetField, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showEditExpressionDialog(memberName: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_expression, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogTvEditTitle)
        val etExpression = dialogView.findViewById<EditText>(R.id.dialogEtFullExpression)
        val tvSubtotal = dialogView.findViewById<TextView>(R.id.dialogTvLiveSubtotal)
        val btnPlus = dialogView.findViewById<Button>(R.id.dialogBtnAddPlus)
        val btnMinus = dialogView.findViewById<Button>(R.id.dialogBtnAddMinus)
        val btnCancel = dialogView.findViewById<Button>(R.id.dialogBtnEditCancel)
        val btnDone = dialogView.findViewById<Button>(R.id.dialogBtnEditDone)

        tvTitle.text = "Expenses for $memberName"

        val currentVal = rawInputMap[memberName] ?: ""
        etExpression.setText(currentVal)
        etExpression.setSelection(currentVal.length)
        tvSubtotal.text = "Total: ₹%.2f".format(parseSum(currentVal))

        etExpression.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tvSubtotal.text = "Total: ₹%.2f".format(parseSum(s.toString()))
            }
        })

        btnPlus.setOnClickListener {
            val text = etExpression.text.toString().trim()
            if (text.isNotEmpty() && !text.endsWith("+") && !text.endsWith("-")) {
                etExpression.append("+")
            }
        }

        btnMinus.setOnClickListener {
            val text = etExpression.text.toString().trim()
            if (text.isNotEmpty() && !text.endsWith("+") && !text.endsWith("-")) {
                etExpression.append("-")
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnDone.setOnClickListener {
            val updated = etExpression.text.toString().trim()
            rawInputMap[memberName] = updated
            spendingMap[memberName] = parseSum(updated)
            adapter.notifyDataSetChanged()
            updateLiveSummary()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        dialog.show()

        val width = (resources.displayMetrics.widthPixels * 0.92).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        etExpression.requestFocus()
    }

    private fun showSingleToast(message: String) {
        activeToast?.cancel()
        activeToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        activeToast?.show()
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

        popupView.findViewById<View>(R.id.menuItemAbout).setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }

        popupWindow.showAsDropDown(anchorView, -30, 10)
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
        showSingleToast("Started fresh trip")
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

        val cleaned = expression.replace(" ", "")
        val regex = Regex("(?=[+-])")
        val tokens = cleaned.split(regex).filter { it.isNotBlank() }

        var sum = 0.0
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed == "+" || trimmed == "-") continue
            val value = trimmed.toDoubleOrNull() ?: 0.0
            sum += value
        }
        return maxOf(0.0, sum)
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
            val btnMinus = view.findViewById<View>(R.id.btnStepMinus)
            val btnPlus = view.findViewById<View>(R.id.btnStepPlus)
            val btnExpand = view.findViewById<View>(R.id.btnExpandExpression)
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

            btnExpand.setOnClickListener {
                showEditExpressionDialog(memberName)
            }

            btnPlus.setOnClickListener {
                val currentText = etAmount.text.toString().trim()
                if (currentText.isNotEmpty() && !currentText.endsWith("+") && !currentText.endsWith("-")) {
                    val updated = "$currentText+"
                    rawInputMap[memberName] = updated
                    etAmount.setText(updated)
                    etAmount.setSelection(updated.length)
                    etAmount.requestFocus()
                }
            }

            btnMinus.setOnClickListener {
                val currentText = etAmount.text.toString().trim()
                if (currentText.isNotEmpty() && !currentText.endsWith("+") && !currentText.endsWith("-")) {
                    val updated = "$currentText-"
                    rawInputMap[memberName] = updated
                    etAmount.setText(updated)
                    etAmount.setSelection(updated.length)
                    etAmount.requestFocus()
                }
            }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAndLoadTripFromIntent(intent)
    }
        private fun checkAndLoadTripFromIntent(sourceIntent: Intent?) {
            val loadTrip = sourceIntent?.getBooleanExtra("LOAD_TRIP_FOR_EDIT", false) ?: false
            if (!loadTrip) return

            sourceIntent?.putExtra("LOAD_TRIP_FOR_EDIT", false)

            val id = sourceIntent?.getLongExtra("TRIP_ID", System.currentTimeMillis()) ?: System.currentTimeMillis()
            val title = sourceIntent?.getStringExtra("TRIP_TITLE") ?: ""
            val savedMembers = sourceIntent?.getStringArrayListExtra("MEMBERS") ?: arrayListOf()
            val savedKeys = sourceIntent?.getStringArrayListExtra("MAP_KEYS") ?: arrayListOf()
            val savedValues = sourceIntent?.getStringArrayListExtra("MAP_VALUES") ?: arrayListOf()

            tripSessionId = id
            etTripTitle.setText(title)

            memberList.clear()
            memberList.addAll(savedMembers)

            rawInputMap.clear()
            for (i in 0 until savedKeys.size) {
                val k = savedKeys[i]
                val v = if (i < savedValues.size) savedValues[i] else ""
                rawInputMap[k] = v
            }

            spendingMap.clear()
            for ((k, v) in rawInputMap) {
                spendingMap[k] = parseSum(v)
            }

            adapter.notifyDataSetChanged()
            updateLiveSummary()
            showSingleToast("Loaded '$title' for editing")
        }
    }