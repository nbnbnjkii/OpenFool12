package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test

class SeatOrderTest {
    @Test fun everySeatGetsClockwiseAttackForEveryPlayerCount() {
        for (count in 2..6) for (first in 0 until count) {
            val out=BooleanArray(count)
            var seat=first
            val visited=mutableListOf<Int>()
            repeat(count) { visited.add(seat);seat=SeatOrder.next(seat,out) }
            assertEquals((0 until count).map { (first+it)%count },visited)
            assertEquals(first,seat)
        }
    }
    @Test fun allAttackersCanThrowNotJustTwoNeighbours() {
        for (count in 2..6) for (first in 0 until count) {
            val out=BooleanArray(count)
            val defender=SeatOrder.next(first,out)
            val done=BooleanArray(count)
            val seen=mutableListOf(first)
            var seat=first
            done[seat]=true
            while (true) {
                val next=SeatOrder.nextThrower(seat,first,defender,out,done,false) ?: break
                assertFalse(next in seen)
                seen.add(next);done[next]=true;seat=next
            }
            assertEquals(SeatOrder.throwers(first,defender,out,false),seen)
            assertEquals(count-1,seen.size)
        }
    }
    @Test fun eliminatedSeatsAreSkippedWithoutSkippingActiveDefender() {
        for (count in 3..6) for (gone in 0 until count) {
            val out=BooleanArray(count);out[gone]=true
            for (seat in 0 until count) {
                val next=SeatOrder.next(seat,out)
                assertFalse(out[next])
                assertEquals((1..count).map { (seat+it)%count }.first { !out[it] },next)
            }
        }
    }
    @Test fun teamsOfSixIncludeAllThreeTeammates() {
        val out=BooleanArray(6)
        assertEquals(listOf(0,2,4),SeatOrder.throwers(0,1,out,true))
        out[2]=true
        assertEquals(listOf(0,4),SeatOrder.throwers(0,1,out,true))
    }
    @Test fun portraitsHaveDistinctClockwiseSeats() {
        for (count in 2..6) {
            val seats=(0 until count).map { SeatOrder.portraitSeat(it,count).toList() }
            assertEquals(count,seats.toSet().size)
            val angles=seats.map { kotlin.math.atan2((it[1]-500f).toDouble(),(it[0]-240f).toDouble()) }
            var previous=angles.first()
            for (angle in angles.drop(1)) {
                var adjusted=angle
                while (adjusted>=previous) adjusted-=2*Math.PI
                assertTrue(previous-adjusted<Math.PI*2)
                previous=adjusted
            }
            assertTrue(angles.first()-previous<Math.PI*2)
        }
    }
}
