package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test

class PortraitTableLayoutTest {
    @Test fun networkFanMatchesLocalFanForEveryHandSizeAndDeckWidth() {
        for(width in listOf(345f,360f,371f,386f))for(count in 1..52)for(i in 0 until count) {
            val expected=HandFan.pose(i,count,width*0.36f,540f*0.36f,true)
            val actual=PortraitTableLayout.hand(i,count,width*0.36f)
            assertEquals(expected.x,actual.x,0.001f)
            assertEquals(expected.y,actual.y,0.001f)
            assertEquals(expected.angle,actual.angle,0.001f)
            assertTrue(actual.x.isFinite());assertTrue(actual.y.isFinite())
        }
    }
    @Test fun attacksUseSameTwoRowsAndOffsetsAsOfflineTable() {
        for(i in 0..5) {
            val (x,y)=PortraitTableLayout.attack(i)
            assertEquals(109f+i*90f+(if(i>=3)-270f else 0f),x,0.001f)
            assertEquals(536f+(if(i>=3)-160f else 0f),y,0.001f)
            assertTrue(PortraitTableLayout.onTable(x+36f,y+54f))
        }
        assertFalse(PortraitTableLayout.onTable(240f,HandFan.PANEL_TOP))
    }
    @Test fun deckStaysBelowWagerAndClearOfPlayerAtTop() {
        assertEquals(332f,PortraitTableLayout.DECK_X,0f)
        assertEquals(804f,PortraitTableLayout.DECK_Y,0f)
        assertTrue(PortraitTableLayout.DECK_Y+PortraitTableLayout.TABLE_CARD_HEIGHT<950f)
        assertTrue(PortraitTableLayout.DECK_X>269f)
    }
}
