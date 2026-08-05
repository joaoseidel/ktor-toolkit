package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.validators.email
import com.luizalabs.ktor.toolkit.validator.validators.min
import com.luizalabs.ktor.toolkit.validator.validators.nil
import com.luizalabs.ktor.toolkit.validator.validators.size
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

private data class Profile(
    val email: String? = null,
    val age: Int? = null,
    val nickname: String? = null,
)

class NullPropertyTest :
    ShouldSpec({
        context("a null optional property") {
            // Class.isInstance(null) is always false, so the type check used to reject every null
            // property with a message about its type — optional fields could not be validated.
            should("not be reported as the wrong type") {
                val context = ValidationContext(Profile())

                context.property(Profile::email) { should be email() }

                context.getErrors() shouldBe emptyList()
            }

            should("stay silent across every rule that has no opinion on absence") {
                val context = ValidationContext(Profile())

                context.property(Profile::email) { should be email() }
                context.property(Profile::age) { should be min(18) }
                context.property(Profile::nickname) { should be size(min = 3) }

                context.getErrors() shouldBe emptyList()
            }

            should("stay silent under negation too") {
                val context = ValidationContext(Profile())

                context.property(Profile::email) { should notBe email() }

                context.getErrors() shouldBe emptyList()
            }
        }

        context("nil, the one rule that is about absence") {
            should("accept a null value") {
                val context = ValidationContext(Profile())

                context.property(Profile::email) { should be nil() }

                context.getErrors() shouldBe emptyList()
            }

            should("reject a null value under negation") {
                val context = ValidationContext(Profile())

                context.property(Profile::email) { should notBe nil() }

                context.getErrors().size shouldBe 1
                context.getErrors()[0].message shouldBe "should not be null"
            }

            should("reject a present value") {
                val context = ValidationContext(Profile(email = "a@b.com"))

                context.property(Profile::email) { should be nil() }

                context.getErrors().size shouldBe 1
                context.getErrors()[0].message shouldBe "should be null"
            }
        }

        context("requiring both presence and validity") {
            should("report only the absence when the field is missing") {
                val context = ValidationContext(Profile())

                context.property(Profile::email) {
                    should notBe nil()
                    should be email()
                }

                context.getErrors().size shouldBe 1
                context.getErrors()[0].message shouldBe "should not be null"
            }

            should("report only the format when the field is present but wrong") {
                val context = ValidationContext(Profile(email = "not-an-email"))

                context.property(Profile::email) {
                    should notBe nil()
                    should be email()
                }

                context.getErrors().size shouldBe 1
                context.getErrors()[0].message shouldBe "should be a valid email address"
            }

            should("report nothing when the field is present and valid") {
                val context = ValidationContext(Profile(email = "a@b.com"))

                context.property(Profile::email) {
                    should notBe nil()
                    should be email()
                }

                context.getErrors() shouldBe emptyList()
            }
        }

        context("a present property of the wrong type") {
            should("still be reported as a type mismatch") {
                val context = ValidationContext(Profile(nickname = "abc"))

                context.property(Profile::nickname) { should be min(3) }

                context.getErrors().size shouldBe 1
                context.getErrors()[0].message shouldBe "should be of type Number"
            }
        }
    })
