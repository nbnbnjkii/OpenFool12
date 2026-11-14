package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test

class BotGameRulesTest {
    private val sevenHearts = Card(Suit.HEARTS, Rank.SEVEN)
    private fun attacks(count: Int = 1): Array<Card?> =
        Array(6) { if (it < count) Card(Suit.SPADES, Rank.SEVEN) else null }
    private fun defenses(): Array<Card?> = arrayOfNulls(6)
    private fun player(pass: Boolean = true): Player =
        Player(RuleSet(allowPass = pass, teamPlay = false), "Test", 0).apply { addCard(sevenHearts) }

    @Test fun supportsTwoThroughSixPlayersWithStandardDecks() {
        for (players in 2..6) for (requested in listOf(24,36,52)) {
            val size = RuleSet.compatibleDeckSize(requested,players)
            assertTrue(size in listOf(24,36,52))
            assertTrue(size >= players*6)
            val rules = RuleSet(playerCount=players,cardCount=size,teamPlay=false)
            val deck = Deck(rules.lowestRank)
            assertEquals(size,deck.cards!!.size)
            repeat(players*6) { assertNotNull(deck.draw()) }
            assertEquals(size-players*6,deck.cards!!.size)
        }
    }
    @Test fun smallDeckCannotSilentlyBecomeThirtyCards() {
        assertEquals(24,RuleSet.compatibleDeckSize(24,4))
        assertEquals(36,RuleSet.compatibleDeckSize(24,5))
        assertEquals(36,RuleSet.compatibleDeckSize(24,6))
        assertEquals(52,RuleSet.compatibleDeckSize(52,6))
    }
    @Test fun passesMatchingRankWithEnoughCapacity() {
        assertTrue(player().cardCanBePassed(sevenHearts,attacks(),defenses(),2))
    }
    @Test fun exactCapacityIsAllowed() {
        assertTrue(player().cardCanBePassed(sevenHearts,attacks(2),defenses(),3))
    }
    @Test fun rejectsInsufficientCapacity() {
        assertFalse(player().cardCanBePassed(sevenHearts,attacks(2),defenses(),2))
    }
    @Test fun passingDisabledInThrowInMode() {
        assertFalse(player(false).cardCanBePassed(sevenHearts,attacks(),defenses(),6))
    }
    @Test fun cannotPassAfterStartingDefense() {
        val defended = defenses().apply { this[0] = Card(Suit.SPADES,Rank.EIGHT) }
        assertFalse(player().cardCanBePassed(sevenHearts,attacks(),defended,6))
    }
    @Test fun cannotPassEmptyOrFullTable() {
        assertFalse(player().cardCanBePassed(sevenHearts,arrayOfNulls(6),defenses(),6))
        assertFalse(player().cardCanBePassed(sevenHearts,attacks(6),defenses(),10))
    }
    @Test fun cannotPassWrongRankOrCardNotInHand() {
        val other = Card(Suit.CLUBS,Rank.EIGHT)
        val p = player().apply { addCard(other) }
        assertFalse(p.cardCanBePassed(other,attacks(),defenses(),6))
        assertFalse(p.cardCanBePassed(Card(Suit.CLUBS,Rank.SEVEN),attacks(),defenses(),6))
    }
    @Test fun cannotPassMixedRanks() {
        val mixed = attacks(2).apply { this[1] = Card(Suit.CLUBS,Rank.NINE) }
        assertFalse(player().cardCanBePassed(sevenHearts,mixed,defenses(),6))
    }
}
