package ru.hyst329.openfool

import com.badlogic.gdx.Preferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class EconomyAndTimerTest {
    private fun preferences(): Preferences {
        val values=HashMap<String,Any>()
        return Proxy.newProxyInstance(Preferences::class.java.classLoader,arrayOf(Preferences::class.java)) { proxy,method,args ->
            when (method.name) {
                "putInteger","putBoolean" -> { values[args[0] as String]=args[1]; proxy }
                "getInteger" -> values[args[0] as String] ?: args.getOrNull(1) ?: 0
                "getBoolean" -> values[args[0] as String] ?: args.getOrNull(1) ?: false
                "flush" -> null
                else -> throw IllegalStateException(method.name)
            }
        } as Preferences
    }
    @Test fun welcomeGrantOnlyOnceAndPurchasesSurviveRestart() {
        val prefs=preferences()
        val wallet=GameEconomy(prefs)
        assertEquals(10000,wallet.coins); assertEquals(20,wallet.crystals)
        assertTrue(wallet.spend(2))
        val reopened=GameEconomy(prefs)
        assertEquals(10000,reopened.coins); assertEquals(18,reopened.crystals)
    }
    @Test fun cannotOverdrawAndFailedPurchaseDoesNotUnlock() {
        val wallet=GameEconomy(preferences())
        assertTrue(wallet.spend(19))
        val perks=GamePerks(wallet)
        assertFalse(perks.unlockDiscard()); assertFalse(perks.discardVisible)
        assertFalse(perks.payUndo()); assertEquals(1,wallet.crystals)
        assertTrue(perks.unlockHighlight()); assertEquals(0,wallet.crystals)
    }
    @Test fun gameUnlocksChargeOnceButUndoChargesPerUse() {
        val wallet=GameEconomy(preferences()); val perks=GamePerks(wallet)
        assertTrue(perks.unlockDiscard()); assertTrue(perks.unlockDiscard())
        assertEquals(17,wallet.crystals)
        assertTrue(perks.unlockHighlight()); assertTrue(perks.unlockHighlight())
        assertEquals(16,wallet.crystals)
        assertTrue(perks.payUndo()); assertTrue(perks.payUndo())
        assertEquals(12,wallet.crystals)
        val nextGame=GamePerks(wallet)
        assertFalse(nextGame.discardVisible); assertFalse(nextGame.highlightEnabled)
        assertTrue(nextGame.unlockDiscard()); assertEquals(9,wallet.crystals)
    }
    @Test(expected=IllegalArgumentException::class) fun negativeCostCannotCreateCrystals() {
        GameEconomy(preferences()).spend(-1)
    }
    @Test fun warningExactlyAtFifteenAndOnlyOncePerDecision() {
        val timer=DecisionTimer()
        assertFalse(timer.tick(0,0,14f).warning)
        assertEquals(16f,timer.remaining,0.001f)
        assertTrue(timer.tick(0,0,1f).warning)
        assertFalse(timer.tick(0,0,1f).warning)
        assertTrue(timer.tick(0,0,14f).timeout)
        assertFalse(timer.tick(0,0,1f).timeout)
        assertEquals(0f,timer.remaining,0.001f)
    }
    @Test fun newDecisionOrPlayerGetsFreshThirtySeconds() {
        val timer=DecisionTimer()
        timer.tick(0,1,20f)
        timer.tick(1,1,0f); assertEquals(30f,timer.remaining,0.001f)
        timer.tick(1,1,15f)
        timer.tick(1,2,0f); assertEquals(30f,timer.remaining,0.001f)
        assertTrue(timer.tick(1,2,15f).warning)
    }
    @Test fun animationsDoNotConsumeDecisionTime() {
        val timer=DecisionTimer()
        timer.tick(0,0,10f)
        timer.tick(-1,0,100f)
        assertEquals(20f,timer.remaining,0.001f)
        assertEquals(0,timer.owner)
    }
    @Test fun cannotReserveMoreThanCoinsOrReserveTwice() {
        val wallet=GameEconomy(preferences())
        assertFalse(wallet.reserveStake(10001,2)); assertFalse(wallet.reserveStake(-1,2))
        assertEquals(10000,wallet.coins)
        assertTrue(wallet.reserveStake(250,2)); assertEquals(9750,wallet.coins)
        assertFalse(wallet.reserveStake(250,2)); assertEquals(9750,wallet.coins)
        assertEquals(20,wallet.crystals)
    }
    @Test fun stakesSettleOnceForWinLossAndDraw() {
        for (players in 2..6) {
            for (outcome in -1..1) {
                val wallet=GameEconomy(preferences())
                assertTrue(wallet.reserveStake(250,players))
                val expected=when { outcome>0 -> 250+250/(players-1); outcome==0 -> 250; else -> 0 }
                assertEquals(expected,wallet.settleStake(outcome))
                assertEquals(9750+expected,wallet.coins)
                assertEquals(0,wallet.settleStake(outcome));assertEquals(0,wallet.refundStake())
                assertEquals(9750+expected,wallet.coins)
            }
        }
    }
    @Test fun exitAndInterruptedAppReturnOnlyTheReservedStake() {
        val prefs=preferences();val wallet=GameEconomy(prefs)
        assertTrue(wallet.reserveStake(500,6))
        val reopened=GameEconomy(prefs)
        assertEquals(10000,reopened.coins);assertEquals(0,reopened.activeStake)
        assertEquals(10000,GameEconomy(prefs).coins)
        assertTrue(reopened.reserveStake(1000,2));assertEquals(1000,reopened.refundStake())
        assertEquals(10000,reopened.coins)
    }
    @Test fun zeroBalanceStillAllowsFreePracticeButNoPaidStake() {
        val wallet=GameEconomy(preferences())
        assertTrue(wallet.reserveStake(10000,2));wallet.settleStake(-1)
        assertEquals(0,wallet.coins)
        assertFalse(wallet.reserveStake(1,2));assertTrue(wallet.reserveStake(0,2))
        assertEquals(0,wallet.coins)
    }

    @Test fun confirmedSurrenderLosesOneStakeAndLaterAiWinCannotPayOut() {
        val wallet=GameEconomy(preferences());val flow=TableFlow(2)
        assertTrue(wallet.reserveStake(250,2))
        if (flow.surrender()) wallet.settleStake(-1)
        if (flow.surrender()) wallet.settleStake(-1)
        assertEquals(9750,wallet.coins)
        assertEquals(0,wallet.settleStake(1))
        assertEquals(0,wallet.refundStake())
        assertEquals(9750,wallet.coins)
    }
    @Test fun rejectingSurrenderDoesNotConsumeReservation() {
        val wallet=GameEconomy(preferences());val flow=TableFlow(2)
        wallet.reserveStake(250,2)
        assertFalse(flow.surrendered)
        assertEquals(250,wallet.activeStake)
        assertEquals(9750,wallet.coins)
        assertEquals(250,wallet.refundStake())
    }

}
