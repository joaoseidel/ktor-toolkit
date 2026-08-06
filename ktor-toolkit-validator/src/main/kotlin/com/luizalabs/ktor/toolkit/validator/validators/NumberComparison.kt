package com.luizalabs.ktor.toolkit.validator.validators

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Compares two numbers of any concrete types, the way the numeric rules need.
 *
 * Every [Number] subtype is handled, not just the four the JVM has operators for: integral values
 * are compared exactly as longs, anything arbitrary-precision as [BigDecimal], and the rest as
 * doubles. Comparing a rule's bound against a property of a different numeric type is therefore
 * fine — `min(3)` on a `BigDecimal` property means what it reads as.
 *
 * @return a negative number, zero or a positive number as this value is less than, equal to or
 * greater than [other].
 */
internal fun Number.compareWith(other: Number): Int =
    when {
        isArbitraryPrecision() || other.isArbitraryPrecision() -> toBigDecimal().compareTo(other.toBigDecimal())
        isIntegral() && other.isIntegral() -> toLong().compareTo(other.toLong())
        else -> toDouble().compareTo(other.toDouble())
    }

/** Whether this number holds a whole value that fits a `Long`. */
internal fun Number.isIntegral(): Boolean = this is Byte || this is Short || this is Int || this is Long

/** Whether this number carries more precision than a `Long` or a `Double` can hold. */
private fun Number.isArbitraryPrecision(): Boolean = this is BigDecimal || this is BigInteger

/** Widens any number to [BigDecimal], going through the decimal form so `0.1` stays `0.1`. */
private fun Number.toBigDecimal(): BigDecimal =
    when (this) {
        is BigDecimal -> this
        is BigInteger -> BigDecimal(this)
        is Double, is Float -> BigDecimal.valueOf(toDouble())
        else -> BigDecimal.valueOf(toLong())
    }
