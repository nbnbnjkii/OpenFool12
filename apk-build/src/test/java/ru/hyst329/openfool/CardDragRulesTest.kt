package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test
import com.badlogic.gdx.scenes.scene2d.EventListener
import kotlin.math.*

class CardDragRulesTest {
    private val attacks: Array<Card?> = arrayOf(Card(Suit.SPADES,Rank.KING),Card(Suit.HEARTS,Rank.SIX),null,null,null,null)
    private val defenses: Array<Card?> = arrayOfNulls(6)
    private val cover = Card(Suit.HEARTS,Rank.SEVEN)
    private fun player() = Player(RuleSet(teamPlay=false),"Human",0).apply { addCard(cover) }

    @Test fun coversTheDroppedOnCardNotFirstOpenCard() {
        val p = player()
        assertFalse(p.cardCanBeBeaten(cover,attacks,defenses,Suit.CLUBS,0))
        assertTrue(p.cardCanBeBeaten(cover,attacks,defenses,Suit.CLUBS,1))
        var chosen = -1
        p.addListener(EventListener { event ->
            if (event is Player.CardBeatenEvent) chosen = event.attackIndex
            false
        })
        p.beatWithCard(cover,attacks,defenses,Suit.CLUBS,1)
        assertEquals(1,chosen)
        assertFalse(p.hand.contains(cover))
    }
    @Test fun wrongEmptyOrAlreadyCoveredTargetDoesNotConsumeCard() {
        val p = player()
        for (index in listOf(-1,0,2,6)) {
            p.beatWithCard(cover,attacks,defenses,Suit.CLUBS,index)
            assertTrue(p.hand.contains(cover))
        }
        defenses[1] = Card(Suit.HEARTS,Rank.EIGHT)
        p.beatWithCard(cover,attacks,defenses,Suit.CLUBS,1)
        assertTrue(p.hand.contains(cover))
    }
    @Test fun defaultAiDefenseSkipsAnOutOfOrderCoveredCard() {
        defenses[1] = cover
        val ace = Card(Suit.SPADES,Rank.ACE)
        val p = player().apply { addCard(ace) }
        assertTrue(p.cardCanBeBeaten(ace,attacks,defenses,Suit.CLUBS))
        assertFalse(p.cardCanBeBeaten(cover,attacks,defenses,Suit.CLUBS))
    }
    @Test fun fullDefenseArrayIsSafelyRejected() {
        for (i in defenses.indices) defenses[i] = cover
        assertFalse(player().cardCanBeBeaten(cover,attacks,defenses,Suit.CLUBS))
    }
    @Test fun fanKeepsEveryCardOnscreenAndAboveControls() {
        val w = 360f*0.36f
        val h = 540f*0.36f
        for (count in 1..52) for (index in 0 until count) {
            val pose = HandFan.pose(index,count,w,h,true)
            val a = Math.toRadians(pose.angle.toDouble())
            for (x in listOf(0f,w)) for (y in listOf(0f,h)) {
                val px = pose.x + cos(a)*x - sin(a)*y
                val py = pose.y + sin(a)*x + cos(a)*y
                assertTrue("left/right edge, hand=$count",px >= 0 && px <= 480)
                assertTrue("hand overlaps HUD or table, hand=$count",py >= 85 && py < 350)
            }
        }
    }
    @Test fun fanAnglesAreSymmetric() {
        for (count in 1..52) {
            val first=HandFan.pose(0,count,129.6f,194.4f,true)
            val last=HandFan.pose(count-1,count,129.6f,194.4f,true)
            assertEquals(first.angle,-last.angle,0.001f)
        }
    }
}
