package com.github.joaoseidel.ktor.toolkit.validator.validators

import java.math.BigDecimal
import java.math.BigInteger

internal fun Number.compareWith(other: Number): Int =
    when {
        isArbitraryPrecision() || other.isArbitraryPrecision() -> toBigDecimal().compareTo(other.toBigDecimal())
        isIntegral() && other.isIntegral() -> toLong().compareTo(other.toLong())
        else -> toDouble().compareTo(other.toDouble())
    }

internal fun Number.isIntegral(): Boolean = this is Byte || this is Short || this is Int || this is Long

private fun Number.isArbitraryPrecision(): Boolean = this is BigDecimal || this is BigInteger

/** Widens any number to [BigDecimal], going through the decimal form so `0.1` stays `0.1`. */
private fun Number.toBigDecimal(): BigDecimal =
    when (this) {
        is BigDecimal -> this
        is BigInteger -> BigDecimal(this)
        is Double, is Float -> BigDecimal.valueOf(toDouble())
        else -> BigDecimal.valueOf(toLong())
    }
