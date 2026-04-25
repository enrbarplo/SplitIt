package com.example.splitit

data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val payerId: String = "",
    val groupId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)