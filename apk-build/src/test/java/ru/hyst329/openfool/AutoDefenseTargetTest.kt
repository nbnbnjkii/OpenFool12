package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test

class AutoDefenseTargetTest {
    private val attacks: Array<Card?> = arrayOf(Card(Suit.SPADES,Rank.KING),
        Card(Suit.HEARTS,Rank.SIX),Card(Suit.HEARTS,Rank.EIGHT),null,null,null)
    private val defenses: Array<Card?> = arrayOfNulls(6)
    private val cover = Card(Suit.HEARTS,Rank.NINE)
    private val player = Player(RuleSet(teamPlay=false),"Human",0).apply { addCard(cover) }

    @Test fun emptySpaceDropFindsLegalTargetWithoutAiming() {
        assertEquals(1,player.defenseTarget(cover,attacks,defenses,Suit.CLUBS))
    }
    @Test fun directLegalTargetTakesPriority() {
        assertEquals(2,player.defenseTarget(cover,attacks,defenses,Suit.CLUBS,2))
    }
    @Test fun wrongTargetFallsBackToCardItCanActuallyBeat() {
        assertEquals(1,player.defenseTarget(cover,attacks,defenses,Suit.CLUBS,0))
    }
    @Test fun coveredTargetsAreSkipped() {
        defenses[1]=Card(Suit.HEARTS,Rank.SEVEN)
        assertEquals(2,player.defenseTarget(cover,attacks,defenses,Suit.CLUBS,1))
    }
    @Test fun noLegalDefenseReturnsNullWithoutConsumingCard() {
        defenses[1]=cover; defenses[2]=Card(Suit.HEARTS,Rank.TEN)
        assertNull(player.defenseTarget(cover,attacks,defenses,Suit.CLUBS))
        assertTrue(player.hand.contains(cover))
    }
    @Test fun trumpCanAutomaticallyCoverOtherSuit() {
        assertEquals(0,player.defenseTarget(cover,attacks,defenses,Suit.HEARTS))
    }
    @Test fun emptyBoardOrCardNotInHandHasNoTarget() {
        assertNull(player.defenseTarget(cover,arrayOfNulls(6),defenses,Suit.CLUBS))
        assertNull(player.defenseTarget(Card(Suit.CLUBS,Rank.ACE),attacks,defenses,Suit.CLUBS))
    }
}
