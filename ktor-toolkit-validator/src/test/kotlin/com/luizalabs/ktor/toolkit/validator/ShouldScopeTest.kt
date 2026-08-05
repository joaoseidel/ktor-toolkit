package com.luizalabs.ktor.toolkit.validator

import io.kotest.core.spec.style.ShouldSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ShouldScopeTest :
    ShouldSpec({
        context("ShouldScope") {
            context("be method") {
                should("call rule.apply with negate=false") {
                    val validator = mockk<PropertyValidator<Any, Any>>()
                    val rule = mockk<ValidationRule>()
                    val scope = ShouldScope(validator)

                    every { rule.apply(validator, false) } returns validator

                    scope be rule

                    verify { rule.apply(validator, false) }
                }
            }

            context("notBe method") {
                should("call rule.apply with negate=true") {
                    val validator = mockk<PropertyValidator<Any, Any>>()
                    val rule = mockk<ValidationRule>()
                    val scope = ShouldScope(validator)

                    every { rule.apply(validator, true) } returns validator

                    scope notBe rule

                    verify { rule.apply(validator, true) }
                }
            }
        }
    })
