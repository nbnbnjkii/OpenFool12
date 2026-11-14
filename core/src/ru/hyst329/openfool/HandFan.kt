package ru.hyst329.openfool

import kotlin.math.*

/** Positions are for origin (0,0), matching Scene2D's drawing and hit transforms. */
internal object HandFan {
    const val PANEL_TOP = 95f
    data class Pose(val x: Float, val y: Float, val angle: Float)
    fun pose(index: Int, count: Int, width: Float, height: Float, portrait: Boolean): Pose {
        val t = if (count <= 1) 0f else 2f * index / (count-1) - 1f
        val spread = min(if (portrait) 130f else 235f, max(0,count-1)*30f)
        val angle = -t * if (portrait) 10f else 14f
        val radians = Math.toRadians(angle.toDouble())
        val cx = (if (portrait) 240f else 400f) + t*spread
        // Portrait: lower half is behind the bottom panel, as in the hand reference.
        val cy = (if (portrait) 90f else 95f) + (1-t*t)*(if (portrait) 12f else 26f)
        return Pose(cx - (cos(radians)*width/2 - sin(radians)*height/2).toFloat(),
            cy - (sin(radians)*width/2 + cos(radians)*height/2).toFloat(), angle)
    }
}
