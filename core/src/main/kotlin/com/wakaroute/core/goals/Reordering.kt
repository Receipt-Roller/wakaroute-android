package com.wakaroute.core.goals

/**
 * Moves one entry to a new position, keeping everything else in order.
 *
 * A pure function rather than list surgery at the call site, because the index
 * arithmetic is the part that goes wrong: moving an item *down* past its own
 * removal shifts every index after it by one, and getting that wrong silently
 * reorders a student's 志望校 into something they did not choose.
 *
 * Out-of-range positions are clamped rather than throwing. The buttons that
 * drive this are disabled at the ends, so an out-of-range call means a race
 * between a tap and a reload — worth surviving, not worth crashing over.
 */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices) return this

    val target = to.coerceIn(0, lastIndex)
    if (from == target) return this

    return toMutableList().apply { add(target, removeAt(from)) }
}
