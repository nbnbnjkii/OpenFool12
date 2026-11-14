package ru.hyst329.openfool

/** Seat numbers run clockwise, independent of how many seats are occupied. */
internal object SeatOrder {
    fun next(from: Int, out: BooleanArray): Int =
        (1..out.size).map { (from + it) % out.size }.firstOrNull { !out[it] } ?: from

    fun throwers(attacker: Int, defender: Int, out: BooleanArray, teams: Boolean): List<Int> =
        (0 until out.size).map { (attacker + it) % out.size }.filter {
            !out[it] && it != defender && (!teams || it % 2 == attacker % 2)
        }

    fun nextThrower(from: Int, attacker: Int, defender: Int, out: BooleanArray,
                    done: BooleanArray, teams: Boolean): Int? {
        val eligible = throwers(attacker, defender, out, teams)
        return (1..out.size).map { (from + it) % out.size }.firstOrNull { it in eligible && !done[it] }
    }

    fun portraitSeat(index: Int, count: Int): FloatArray {
        if (index == 0) return floatArrayOf(210f,25f)
        val seats = when (count) {
            2 -> arrayOf(205f to 825f)
            3 -> arrayOf(20f to 635f,395f to 635f)
            4 -> arrayOf(20f to 635f,205f to 825f,395f to 635f)
            5 -> arrayOf(20f to 370f,20f to 635f,395f to 635f,395f to 370f)
            else -> arrayOf(20f to 370f,20f to 635f,205f to 825f,395f to 635f,395f to 370f)
        }
        val p=seats[index-1]
        return floatArrayOf(p.first,p.second)
    }
}
