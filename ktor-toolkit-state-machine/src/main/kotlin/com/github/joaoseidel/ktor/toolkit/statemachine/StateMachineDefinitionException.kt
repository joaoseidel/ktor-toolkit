package com.github.joaoseidel.ktor.toolkit.statemachine

/**
 * Raised by [stateMachine] when the definition itself does not hold together.
 *
 * An unreachable state, a dead end, two transitions on the same event or a target nobody declared
 * are all bugs in the machine rather than in a request, so they surface where the machine is built
 * — at startup, on the line that declares it — instead of on the one request that happens to walk
 * that path in production.
 *
 * There is nothing to catch: the fix is always to correct the definition.
 */
class StateMachineDefinitionException internal constructor(
    message: String,
) : IllegalStateException(message)
