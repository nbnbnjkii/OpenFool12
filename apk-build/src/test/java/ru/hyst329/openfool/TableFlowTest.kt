package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test

class TableFlowTest {
    @Test fun botsCannotStartBeforeHumanReady() {
        for (players in 2..6) {
            val flow=TableFlow(players)
            flow.tick(100f)
            assertTrue((1 until players).all { flow.ready[it] })
            assertFalse(flow.ready[0]); assertFalse(flow.playing)
            assertEquals(TableFlow.Phase.WAITING,flow.phase)
            flow.dealt(); assertEquals(TableFlow.Phase.WAITING,flow.phase)
        }
    }
    @Test fun everySeatMustBeReadyBeforeAnnouncementAndDeal() {
        val flow=TableFlow(6)
        flow.humanReady(); flow.tick(0.4f)
        assertEquals(TableFlow.Phase.WAITING,flow.phase)
        flow.tick(2f)
        assertTrue(flow.ready.all { it })
        assertEquals(TableFlow.Phase.ANNOUNCING,flow.phase)
        assertFalse(flow.playing)
        flow.tick(0.61f); assertEquals(TableFlow.Phase.DEALING,flow.phase)
        assertFalse(flow.playing)
        flow.dealt(); assertTrue(flow.playing)
        flow.tick(100f); assertTrue(flow.playing)
    }
    @Test fun surrenderIsIdempotentAndDoesNotExitTheTable() {
        val flow=TableFlow(2)
        flow.humanReady();flow.tick(1f);flow.tick(1f);flow.dealt()
        assertTrue(flow.surrender()); assertTrue(flow.playing)
        assertTrue(flow.surrendered); assertFalse(flow.surrender())
        assertTrue(flow.playing)
    }
    @Test fun surrenderBeforeDealAutomaticallyReadiesHumanButStillWaitsForBots() {
        val flow=TableFlow(6)
        assertTrue(flow.surrender())
        assertTrue(flow.ready[0]);assertEquals(TableFlow.Phase.WAITING,flow.phase)
        flow.tick(2f);assertEquals(TableFlow.Phase.ANNOUNCING,flow.phase)
    }
    @Test fun yourTurnOnlyInEmptyActionSlotAndRealHumanDecision() {
        val empty=TurnControls.Actions()
        assertTrue(TurnControls.showYourTurn(empty,true,true,false,false))
        for (action in listOf(TurnControls.Actions(take=true),TurnControls.Actions(done=true),TurnControls.Actions(give=true)))
            assertFalse(TurnControls.showYourTurn(action,true,true,false,false))
        assertFalse(TurnControls.showYourTurn(empty,false,true,false,false))
        assertFalse(TurnControls.showYourTurn(empty,true,false,false,false))
        assertFalse(TurnControls.showYourTurn(empty,true,true,true,false))
        assertFalse(TurnControls.showYourTurn(empty,true,true,false,true))
    }
}
