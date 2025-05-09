package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class PropertyValidatorTest :
    ShouldSpec({
        context("PropertyValidator") {
            data class TestClass(
                val testProperty: String,
            )

            context("initialization") {
                should("set propertyPath to property name") {
                    val target = TestClass("test value")
                    val property = TestClass::testProperty
                    val errors = mutableListOf<ValidationError>()

                    val validator = PropertyValidator(target, property, errors)

                    validator.propertyPath shouldBe "testProperty"
                }

                should("set propertyValue to property value from target") {
                    val target = TestClass("test value")
                    val property = TestClass::testProperty
                    val errors = mutableListOf<ValidationError>()

                    val validator = PropertyValidator(target, property, errors)

                    validator.propertyValue shouldBe "test value"
                }
            }

            context("error handling") {
                should("add validation error to errors list") {
                    val target = TestClass("test value")
                    val property = TestClass::testProperty
                    val errors = mutableListOf<ValidationError>()
                    val validator = PropertyValidator(target, property, errors)

                    validator.addError("test error message")

                    errors.size shouldBe 1
                    errors[0].propertyPath shouldBe "testProperty"
                    errors[0].message shouldBe "test error message"
                }
            }

            context("should property") {
                should("return a ShouldScope instance") {
                    val target = TestClass("test value")
                    val property = TestClass::testProperty
                    val errors = mutableListOf<ValidationError>()
                    val validator = PropertyValidator(target, property, errors)

                    validator.should.shouldBeInstanceOf<ShouldScope<*, *>>()
                }
            }
        }
    })
