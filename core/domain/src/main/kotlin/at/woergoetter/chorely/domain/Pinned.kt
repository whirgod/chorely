package at.woergoetter.chorely.domain

import java.time.Clock

/**
 * This clock stopped at one instant in one zone, for the length of one operation.
 *
 * The app's clock follows the device — its zone as well as its time — so an operation that
 * reads it twice can straddle a zone change or a midnight, and bucket the agenda by one day
 * while catching up by another, or mark tomorrow seen having shown today. Anything that reads
 * the injected clock more than once per operation takes one of these first and uses only it.
 */
fun Clock.pinned(): Clock = Clock.fixed(instant(), zone)
