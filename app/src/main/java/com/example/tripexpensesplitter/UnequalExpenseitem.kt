package com.example.tripexpensesplitter

import java.io.Serializable

data class UnequalExpenseItem(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val totalAmount: Double,
    val paidByMap: Map<String, Double>,     // Member -> Amount physically paid
    val consumedByMap: Map<String, Double>  // Member -> Amount consumed / owes
) : Serializable