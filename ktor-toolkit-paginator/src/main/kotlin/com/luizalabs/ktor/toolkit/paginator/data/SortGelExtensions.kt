package com.luizalabs.ktor.toolkit.paginator.data

import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.DESC
import io.github.joaoseidel.geldsl.core.GelOrderingExpression
import io.github.joaoseidel.geldsl.core.entity.properties.GelPropertyRef
import io.github.joaoseidel.geldsl.core.entity.properties.asc
import io.github.joaoseidel.geldsl.core.entity.properties.desc

fun Sort.toGelOrderingExpression(vararg sortableProps: GelPropertyRef): GelOrderingExpression {
    val property =
        sortableProps.find {
            it.name == property
        } ?: throw IllegalArgumentException("Property \"$property\" does not match any sortable property. Is it misspelled?")

    return when (direction) {
        ASC -> property.asc()
        DESC -> property.desc()
    }
}

fun List<Sort>.toGelOrderingExpression(vararg sortableProps: GelPropertyRef): List<GelOrderingExpression> =
    map { it.toGelOrderingExpression(*sortableProps) }

fun Sort.toGelOrderingExpression(sortableProps: List<GelPropertyRef>): GelOrderingExpression =
    this.toGelOrderingExpression(*sortableProps.toTypedArray())

fun List<Sort>.toGelOrderingExpression(sortableProps: List<GelPropertyRef>): List<GelOrderingExpression> =
    this.toGelOrderingExpression(*sortableProps.toTypedArray())
