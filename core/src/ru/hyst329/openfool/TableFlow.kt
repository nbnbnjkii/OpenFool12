package ru.hyst329.openfool

/** Pre-game readiness is distinct from the rules engine's READY (an attacking decision). */
internal class TableFlow(players: Int) {
    enum class Phase { WAITING, ANNOUNCING, DEALING, PLAYING, ENDED }
    val ready=BooleanArray(players)
    var phase=Phase.WAITING; private set
    var surrendered=false; private set
    private var elapsed=0f
    val playing: Boolean get() = phase==Phase.PLAYING
    val needsExitWarning: Boolean get() = !surrendered && ready[0]
    val showReady: Boolean get() = surrendered || (phase==Phase.WAITING && !ready[0])
    fun humanReady() { if (phase==Phase.WAITING) ready[0]=true }
    fun tick(delta: Float) {
        elapsed+=delta.coerceAtLeast(0f)
        if (phase==Phase.WAITING) {
            for (i in 1 until ready.size) if (elapsed>=i*0.35f) ready[i]=true
            if (ready.all { it }) { phase=Phase.ANNOUNCING;elapsed=0f }
        } else if (phase==Phase.ANNOUNCING && elapsed>=0.6f) phase=Phase.DEALING
    }
    fun dealt() { if (phase==Phase.DEALING) phase=Phase.PLAYING }
    /** True only once: repeated flag/Back presses must never debit a second stake. */
    fun surrender(): Boolean {
        if (surrendered) return false
        surrendered=true; phase=Phase.ENDED; ready.fill(false); return true
    }
}
