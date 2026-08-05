package com.luizalabs.ktor.toolkit.validator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ValidationContextTest :
    FunSpec({
        context("ValidationContext") {
            data class Address(
                val street: String?,
                val city: String?,
            )

            data class User(
                val name: String?,
                val address: Address?,
            )

            test("property should create and return a PropertyValidator with correct setup") {
                val user = User("John", Address("Main St", "New York"))
                val context = ValidationContext(user)

                val validator = context.property(User::name) {}

                validator.target shouldBe user
                validator.property shouldBe User::name
                validator.propertyPath shouldBe "name"
                validator.propertyValue shouldBe "John"
            }

            test("property should execute the provided block") {
                val user = User("", Address("Main St", "New York"))
                val context = ValidationContext(user)

                context.property(User::name) {
                    addError("should not be blank")
                }

                context.getErrors().size shouldBe 1
                context.getErrors()[0].propertyPath shouldBe "name"
                context.getErrors()[0].message shouldBe "should not be blank"
            }

            test("nested should add error when nested property is null") {
                val user = User("John", null)
                val context = ValidationContext(user)

                context.nested(User::address) {}

                context.getErrors().size shouldBe 1
                context.getErrors()[0].propertyPath shouldBe "address"
                context.getErrors()[0].message shouldBe "should not be null"
            }

            test("nested should use a custom error message when nested property is null") {
                val user = User("John", null)
                val context = ValidationContext(user)

                context.nested(User::address, nullMessage = "custom message") {}

                context.getErrors().size shouldBe 1
                context.getErrors()[0].propertyPath shouldBe "address"
                context.getErrors()[0].message shouldBe "custom message"
            }

            test("nested should not add errors when nested property exists and has no errors") {
                val user = User("John", Address("Main St", "New York"))
                val context = ValidationContext(user)

                context.nested(User::address) {}

                context.getErrors() shouldBe emptyList()
            }

            test("nested should propagate errors from nested context") {
                val user = User("John", Address(null, "New York"))
                val context = ValidationContext(user)

                context.nested(User::address) {
                    property(Address::street) {
                        addError("should not be null")
                    }
                }

                context.getErrors().size shouldBe 1
                context.getErrors()[0].propertyPath shouldBe "address.street"
                context.getErrors()[0].message shouldBe "should not be null"
            }

            test("nested should propagate multiple errors from nested context") {
                val user = User("John", Address(null, null))
                val context = ValidationContext(user)

                context.nested(User::address) {
                    property(Address::street) {
                        addError("should not be null")
                    }
                    property(Address::city) {
                        addError("should not be null")
                    }
                }

                context.getErrors().size shouldBe 2
                context.getErrors()[0].propertyPath shouldBe "address.street"
                context.getErrors()[0].message shouldBe "should not be null"
                context.getErrors()[1].propertyPath shouldBe "address.city"
                context.getErrors()[1].message shouldBe "should not be null"
            }

            test("getErrors should return list of validation errors") {
                val user = User(null, null)
                val context = ValidationContext(user)

                context.property(User::name) {
                    addError("should not be null")
                }

                context.nested(User::address) {}

                val errors = context.getErrors()
                errors.size shouldBe 2
                errors[0].propertyPath shouldBe "name"
                errors[0].message shouldBe "should not be null"
                errors[1].propertyPath shouldBe "address"
                errors[1].message shouldBe "should not be null"
            }

            test("hasErrors should return true when there are errors") {
                val user = User(null, null)
                val context = ValidationContext(user)

                context.property(User::name) {
                    addError("should not be null")
                }

                context.hasErrors shouldBe true
            }

            test("hasErrors should return false when there are no errors") {
                val user = User("John", Address("Main St", "New York"))
                val context = ValidationContext(user)

                context.hasErrors shouldBe false
            }
        }
    })
