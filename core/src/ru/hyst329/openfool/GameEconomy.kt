package ru.hyst329.openfool

import com.badlogic.gdx.Preferences

/** Local-only saved currency; the welcome grant is made once, never on every launch. */
internal class GameEconomy(private val prefs: Preferences) {
    init {
        if (!prefs.getBoolean("Wallet/Initialized",false)) {
            prefs.putInteger("Wallet/Coins",10000).putInteger("Wallet/Crystals",20)
                .putBoolean("Wallet/Initialized",true).flush()
        }
    }
    val coins: Int get() = prefs.getInteger("Wallet/Coins",0)
    val crystals: Int get() = prefs.getInteger("Wallet/Crystals",0)
    fun spend(cost: Int): Boolean {
        require(cost >= 0)
        if (crystals < cost) return false
        prefs.putInteger("Wallet/Crystals",crystals-cost).flush()
        return true
    }
}

/** Purchases are scoped to one GameScreen, whereas the wallet is persistent. */
internal class GamePerks(private val economy: GameEconomy) {
    var discardVisible = false; private set
    var highlightEnabled = false; private set
    fun unlockDiscard(): Boolean {
        if (discardVisible) return true
        if (!economy.spend(3)) return false
        discardVisible = true; return true
    }
    fun unlockHighlight(): Boolean {
        if (highlightEnabled) return true
        if (!economy.spend(1)) return false
        highlightEnabled = true; return true
    }
    fun payUndo(): Boolean = economy.spend(2)
}
