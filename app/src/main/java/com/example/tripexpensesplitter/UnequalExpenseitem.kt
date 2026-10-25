package com.example.tripexpensesplitter

import java.io.Serializable

data class UnequalExpenseItem(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val amount: Double,
    val paidBy: String,
    val participants: List<String>
) : Serializable