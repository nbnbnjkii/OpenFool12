package ru.hyst329.openfool

import kotlin.math.*

internal enum class MatchMode { BOTS, ONLINE, FRIENDS }

/** A logarithmic 100..10M scale shared by all three setup modes. */
internal object WagerPolicy {
    const val MAX = 10000000
    fun clamp(amount: Int,balance: Int): Int = amount.coerceIn(0,balance.coerceIn(0,MAX))
    fun position(amount: Int): Float = if (amount < 100) 0f else
        ((log10(amount.toDouble())-2.0)/5.0).toFloat().coerceIn(0f,1f)
    fun amount(position: Float,balance: Int): Int {
        val raw=10.0.pow(2.0+position.coerceIn(0f,1f)*5.0)
        val step=when { raw<1000 -> 25; raw<10000 -> 100; raw<100000 -> 1000; raw<1000000 -> 10000; else -> 100000 }
        return clamp((round(raw/step)*step).toInt(),balance)
    }
    fun label(amount: Int): String = when {
        amount>=1000000 && amount%1000000==0 -> "${amount/1000000}M"
        amount>=1000 && amount%1000==0 -> "${amount/1000}K"
        else -> "$amount"
    }
}
