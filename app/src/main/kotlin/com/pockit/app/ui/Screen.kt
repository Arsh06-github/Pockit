package com.pockit.app.ui

sealed class Screen {
    object Home : Screen()
    data class Thread(val personId: Long) : Screen()
    data class Group(val groupId: Long) : Screen()
    object MyExpenses : Screen()
}
