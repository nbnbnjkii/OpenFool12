package ru.hyst329.openfool

/** Shared geometry for local and server-owned portrait tables (480 x 1000). */
internal object PortraitTableLayout {
    const val DECK_X=332f
    const val DECK_Y=804f
    const val TABLE_CARD_WIDTH=72f
    const val TABLE_CARD_HEIGHT=108f
    const val HAND_CARD_WIDTH=129.6f
    const val HAND_CARD_HEIGHT=194.4f
    fun hand(index: Int,count: Int,width: Float=HAND_CARD_WIDTH) = HandFan.pose(index,count,width,HAND_CARD_HEIGHT,true)
    fun attack(index: Int): Pair<Float,Float> = (109f+(index%3)*90f) to (536f-(index/3)*160f)
    fun onTable(x: Float,y: Float) = x in 90f..390f && y in 355f..695f
}
