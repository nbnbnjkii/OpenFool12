package ru.hyst329.openfool

import com.badlogic.gdx.Preferences

/** Local-only saved currency; the welcome grant is made once, never on every launch. */
internal class GameEconomy(private val prefs: Preferences) {
    init {
        if (!prefs.getBoolean("Wallet/Initialized",false)) {
            prefs.putInteger("Wallet/Coins",10000).putInteger("Wallet/Crystals",20)
                .putBoolean("Wallet/Initialized",true).flush()
        }
        // No game-state restore exists yet: return an interrupted match's escrow on app restart.
        refundStake()
    }
    val coins: Int get() = prefs.getInteger("Wallet/Coins",0)
    val crystals: Int get() = prefs.getInteger("Wallet/Crystals",0)
    val activeStake: Int get() = prefs.getInteger("Wager/Amount",0)
    fun reserveStake(amount: Int,players: Int): Boolean {
        if (amount < 0 || amount > WagerPolicy.MAX || amount > coins || activeStake != 0 || players !in 2..6) return false
        prefs.putInteger("Wallet/Coins",coins-amount).putInteger("Wager/Amount",amount)
            .putInteger("Wager/Players",players).flush()
        return true
    }
    /** outcome: positive=win, zero=draw, negative=loss. Each reservation settles at most once. */
    fun settleStake(outcome: Int): Int {
        val stake=activeStake
        if (stake<=0) return 0
        val players=prefs.getInteger("Wager/Players",2).coerceIn(2,6)
        val payout=when { outcome>0 -> stake+stake/(players-1); outcome==0 -> stake; else -> 0 }
        prefs.putInteger("Wallet/Coins",(coins.toLong()+payout).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .putInteger("Wager/Amount",0).flush()
        return payout
    }
    fun refundStake(): Int = settleStake(0)
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
