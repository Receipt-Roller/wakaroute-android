package com.wakaroute.core.goals

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderingTest {

    private val list = listOf("a", "b", "c", "d")

    @Test
    fun `moving up swaps with the entry above`() {
        assertEquals(listOf("a", "c", "b", "d"), list.moved(from = 2, to = 1))
    }

    @Test
    fun `moving down lands where the student expects`() {
        // The case the arithmetic gets wrong. Removing index 1 first shifts
        // everything after it, so a naive add(to) puts the item one place short.
        assertEquals(listOf("a", "c", "b", "d"), list.moved(from = 1, to = 2))
    }

    @Test
    fun `moving to the top makes it 第一志望`() {
        assertEquals(listOf("d", "a", "b", "c"), list.moved(from = 3, to = 0))
    }

    @Test
    fun `moving to the bottom keeps the rest in order`() {
        assertEquals(listOf("b", "c", "d", "a"), list.moved(from = 0, to = 3))
    }

    @Test
    fun `a move to the same place changes nothing`() {
        assertEquals(list, list.moved(from = 2, to = 2))
    }

    @Test
    fun `out of range positions are survived, not thrown`() {
        // Reachable by a tap racing a reload. Clamped rather than crashing.
        assertEquals(list, list.moved(from = 9, to = 0))
        assertEquals(listOf("b", "c", "d", "a"), list.moved(from = 0, to = 99))
        assertEquals(listOf("d", "a", "b", "c"), list.moved(from = 3, to = -5))
    }

    @Test
    fun `a single entry and an empty list are both fine`() {
        assertEquals(listOf("a"), listOf("a").moved(from = 0, to = 0))
        assertEquals(emptyList<String>(), emptyList<String>().moved(from = 0, to = 1))
    }

    @Test
    fun `nothing is lost or duplicated, whatever the move`() {
        for (from in list.indices) {
            for (to in list.indices) {
                val result = list.moved(from, to)
                assertEquals("size changed moving $from to $to", list.size, result.size)
                assertEquals("contents changed moving $from to $to", list.toSet(), result.toSet())
            }
        }
    }
}
