package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test

class TurnControlsTest {
    private fun actions(attacks: Int = 1, defenses: Int = 0, defender: Boolean = false,
                        thrower: Boolean = true, taking: Boolean = false, settled: Boolean = true,
                        dragging: Boolean = false, done: Boolean = false) = TurnControls.actions(
        settled,dragging,attacks,defenses,defender,thrower,taking,done)

    @Test fun noButtonsBeforeFirstAttack() {
        assertEquals(TurnControls.Actions(),actions(attacks=0))
    }
    @Test fun noTakeOnHumanAttackingTurn() {
        assertEquals(TurnControls.Actions(),actions())
    }
    @Test fun showDoneOnlyAfterBotCoveredEveryAttack() {
        assertFalse(actions(attacks=2,defenses=1).done)
        assertEquals(TurnControls.Actions(done=true),actions(attacks=2,defenses=2))
    }
    @Test fun showTakeWhenBotTransfersToHuman() {
        assertEquals(TurnControls.Actions(take=true),actions(attacks=2,defender=true,thrower=false))
    }
    @Test fun showTakeWithPartiallyCoveredHumanDefense() {
        assertEquals(TurnControls.Actions(take=true),actions(attacks=3,defenses=2,defender=true,thrower=false))
    }
    @Test fun humanDefenseNeverShowsDone() {
        assertEquals(TurnControls.Actions(),actions(defenses=1,defender=true,thrower=false))
        assertEquals(TurnControls.Actions(),actions(defenses=1,defender=true,thrower=true))
    }
    @Test fun takeDisappearsAfterDeclaringTake() {
        assertEquals(TurnControls.Actions(),actions(defender=true,thrower=false,taking=true))
    }
    @Test fun takingBotGetsGiveInsteadOfDone() {
        assertEquals(TurnControls.Actions(give=true),actions(taking=true))
    }
    @Test fun noActionsWhileAnimatingOrDragging() {
        assertEquals(TurnControls.Actions(),actions(defenses=1,settled=false))
        assertEquals(TurnControls.Actions(),actions(defenses=1,dragging=true))
        assertEquals(TurnControls.Actions(),actions(defender=true,thrower=false,settled=false))
    }
    @Test fun cannotFinishTwiceOrFinishOnSomeoneElsesTurn() {
        assertEquals(TurnControls.Actions(),actions(defenses=1,done=true))
        assertEquals(TurnControls.Actions(),actions(defenses=1,thrower=false))
    }
    @Test fun fullyDefendedHumanRoundIsAutomaticallyFinishedByBots() {
        assertTrue(TurnControls.botFinishesDefendedHumanTurn(true,3,3))
        assertFalse(TurnControls.botFinishesDefendedHumanTurn(true,3,2))
        assertFalse(TurnControls.botFinishesDefendedHumanTurn(true,0,0))
        assertFalse(TurnControls.botFinishesDefendedHumanTurn(false,3,3))
    }
}
