package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test

class WagerPolicyTest {
    @Test fun thumbNeverExceedsWalletIncludingZeroAndSmallBalances() {
        for (balance in listOf(0,1,50,99,100,250,1000,10000,10000000,Int.MAX_VALUE)) {
            var previous=-1
            for (i in -10..110) {
                val amount=WagerPolicy.amount(i/100f,balance)
                assertTrue(amount>=0)
                assertTrue(amount<=balance)
                assertTrue(amount<=WagerPolicy.MAX)
                assertTrue(amount>=previous)
                previous=amount
            }
        }
    }
    @Test fun scaleMatchesReferenceTicks() {
        val ticks=listOf(100,1000,10000,100000,1000000,10000000)
        ticks.forEachIndexed { i,amount ->
            assertEquals(i/5f,WagerPolicy.position(amount),0.0001f)
            assertEquals(amount,WagerPolicy.amount(i/5f,WagerPolicy.MAX))
        }
    }
    @Test fun savedStakeIsClampedAndDefault250IsRepresentable() {
        assertEquals(250,WagerPolicy.amount(WagerPolicy.position(250),10000))
        assertEquals(10000,WagerPolicy.clamp(1000000,10000))
        assertEquals(0,WagerPolicy.clamp(250,0))
        assertEquals(50,WagerPolicy.amount(1f,50))
    }
    @Test fun allThreeSetupModesUseTheSamePolicy() {
        for (mode in MatchMode.values()) {
            assertEquals("$mode",10000,WagerPolicy.amount(1f,10000))
        }
    }
}
