package ru.hyst329.openfool

/** A fresh deadline for each decision; animation frames pause rather than consume it. */
internal class DecisionTimer {
    var owner: Int = -1; private set
    var remaining = 30f; private set
    private var serial = -1
    private var warned = false
    private var expired = false
    data class Tick(val warning: Boolean = false, val timeout: Boolean = false)
    fun tick(activeOwner: Int, decision: Int, delta: Float): Tick {
        if (activeOwner < 0) return Tick()
        if (owner != activeOwner || serial != decision) {
            owner = activeOwner; serial = decision; remaining = 30f; warned = false; expired = false
        }
        remaining = (remaining-delta.coerceAtLeast(0f)).coerceAtLeast(0f)
        val warning = !warned && remaining <= 15f
        val timeout = !expired && remaining <= 0f
        if (warning) warned = true
        if (timeout) expired = true
        return Tick(warning,timeout)
    }
}
