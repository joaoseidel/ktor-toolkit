---
name: state-machine
description: >-
    Aggregate lifecycles with ktor-toolkit-state-machine: the stateMachine<S, E, C> { } DSL, guards
    as affordances, and publishing available transitions as _links. Use whenever an aggregate has a
    status field with rules about what may follow what, when a use case is about to check
    `if (order.state != PLACED)`, when a `when (state)` chain appears in domain logic, and whenever
    a client needs to know which actions it may take on a resource right now.
---

# Aggregate lifecycles

## What belongs in a machine

One question: **does the field have legal moves?**

A `status` where `PAID` may only follow `PLACED`, and `SHIPPED` only `PAID`, has legal moves. The rules exist whether or not anyone wrote them down,
and they are currently spread across whichever use cases happen to touch the field. A `lastSeenAt` has no legal moves; it is derived data, and
wrapping it in a machine buys nothing.

Two more that look like lifecycles and are not:

- **A boolean.** `isArchived` toggling both ways is not worth a machine. Two states with a move each is more ceremony than `copy(archived = true)`.
- **A field the client sets freely.** If any value may follow any other, there is nothing to declare. That is a validation problem. Load
  `ktor-toolkit:validation`.

## Before writing one, ask

Getting the graph wrong ships as a 409 on a legitimate request, and the toolkit cannot guess a lifecycle. Ask directly:

- What are the **states**, and what is the name of each one in the business's own vocabulary?
- Which are **terminal**, where the story is over once reached?
- Which **moves** are legal, and what event causes each?
- What **blocks** a move that would otherwise be legal? (These become guards.)
- What must **happen** when a move is made: a notification, an audit row, a downstream call? These go in the use case, after the write. See
  [Effects](#effects-and-what-may-go-in-them).

Then propose what they did not think to mention. These are absent far more often than they are declined:

| Propose                                                 | Because                                                               |
|---------------------------------------------------------|-----------------------------------------------------------------------|
| A terminal `CANCELLED` reachable from every early state | Something always goes wrong, and there is otherwise nowhere to put it |
| A guard on the move that spends money or ships goods    | The expensive move is the one worth refusing twice                    |
| An audit row written beside every state change          | "Who moved this to `REFUNDED`, and when?" is asked eventually         |
| Publishing the available moves as `_links`              | Otherwise every client reimplements the lifecycle to draw a button    |

Say which ones you added on your own initiative, so the user can reject them.

## Where the machine lives

**In `-core`, beside the aggregate it describes, as a top-level `val`.** It is immutable and holds no per-subject state, so one instance serves every
request.

```kotlin
// -core/order/OrderFlow.kt
val orderFlow = stateMachine<OrderState, OrderEvent, Order> { … }
```

The module has **no dependencies at all**, no Ktor and no serialization, so it is one of the few toolkit modules `-core` may depend on without the
`ktor-toolkit:architecture` grep lighting up. The `web` package is the exception: `transitionLinks` needs `ktor-toolkit-hateoas`, and it is imported
from `-adapters/web`, never from `-core`.

Declaring it at the top level is not a style preference. The definition is checked when it is built, so a top-level `val` fails at class-load, on
startup and in CI, while one built inside a function fails on the first request that calls it.

A top-level `val` also cannot reach an injected port, and that is a feature: the machine stays a pure description of the lifecycle, and nothing in it
can do I/O behind the use case's back.

## The DSL

```kotlin
enum class OrderState { DRAFT, PLACED, PAID, SHIPPED, CANCELLED }

sealed interface OrderEvent {
    data class Place(val by: UserId) : OrderEvent
    data object Pay : OrderEvent
    data class Ship(val carrier: String) : OrderEvent
    data class Cancel(val reason: String) : OrderEvent
}

val orderFlow = stateMachine<OrderState, OrderEvent, Order> {
    initial(DRAFT)
    final(SHIPPED, CANCELLED)

    state(DRAFT) {
        on<Place>(PLACED) {
            guard("must have at least one line") { it.lines.isNotEmpty() }
        }
        on<Cancel>(CANCELLED)
    }

    state(PLACED) {
        on<Pay>(PAID)
        on<Cancel>(CANCELLED)
    }

    state(PAID) {
        on<Ship>(SHIPPED) { rel = "dispatch" }
    }
}
```

The three type parameters are the state, the event and **the subject**, the thing the moves are about, usually the aggregate itself. Guards
receive it.

### Effects, and what may go in them

The DSL also has hooks that run when a move is accepted:

| Construct             | Runs                                                |
|-----------------------|-----------------------------------------------------|
| `guard(reason) { … }` | Before anything, to decide whether the move happens |
| `onExit { … }`        | On the way out of the source state                  |
| `effect { … }`        | For this move alone, after `onExit`                 |
| `onEnter { … }`       | On arrival, however the state was reached           |
| `onTransition { … }`  | After every accepted move, whichever one it was     |

All of them may suspend, and on a rejection none of them run. But they run inside `fire`, **before the caller has persisted anything**, and the
machine is a top-level `val` with no ports in reach. So a hook that sends an email, charges a card or writes an audit row is a dual write: the side
effect lands, the save that follows fails or loses a race, and the world now records a move the database never made.

Keep the hooks to what is safe to have happened for nothing, such as a metric or a debug log line. Everything else belongs in the use case, after the
state change is written:

```kotlin
class PayOrder(
    private val orders: OrderRepository,
    private val receipts: ReceiptIssuer,
) {
    suspend operator fun invoke(id: OrderId): Order {
        val order = orders.findById(id) ?: throw OrderNotFoundException(id)
        val next = orderFlow.fireOrThrow(order, order.state, Pay)
        val paid = orders.moveState(order, from = order.state, to = next) ?: throw OrderChangedConcurrentlyException(id)

        receipts.issue(paid)
        return paid
    }
}
```

An audit row belongs in the same transaction as the state change, so the repository writes it inside `moveState`. A message to another system
belongs in an outbox table written in that transaction too, and published after commit. A direct call can still succeed for a move that is then
rolled back.

**Event matching is by instance**, so declaring a sealed parent covers every subtype, which is useful as a catch-all after the specific moves.
Declaring the parent *first* would shadow the ones after it, and that is refused at build time rather than silently applied.

## Guards see the subject, never the event

In `guard("must have at least one line") { it.lines.isNotEmpty() }` the lambda takes the order, and there is no way to reach the event from inside
it.

That is the point. It is what lets the machine answer **"what can this order do right now?"** without being handed an event to test with, which is
the question `availableFor` and `transitionLinks` exist to answer. A guard that depended on the payload could not be evaluated until someone had
already decided to fire something, and the affordances would be a second, drifting copy of the rules.

So a check that needs the event's payload is not a guard:

- **Is the payload well formed**, as in a carrier code that matches a pattern, a non-empty reason? That is request validation. Load
  `ktor-toolkit:validation`.
- **Does the payload agree with the world**: is that carrier one we have a contract with? That is business validation, in the use case, failing with
  its own domain exception.

Write the guard's reason for whoever made the request: `"must have at least one line"`, not `"lines.isEmpty"`. It is reported verbatim as
`RejectionReason.GuardFailed`, and it is what the client reads.

## Firing

The machine never touches the subject. It reports where to go, and the use case applies it. `PayOrder` above is the whole shape. Firing happens in
`-core`, in a use case; a route never fires an event itself.

Where a rejection is never routine, as with a `POST /orders/{id}/pay` that has nothing useful to do with one, use `fireOrThrow`, which returns the
state and raises `IllegalTransitionException`. Where it is an ordinary outcome, `fire` returns a result to branch on:

```kotlin
when (val result = orderFlow.fire(order, order.state, Cancel(reason))) {
    is TransitionResult.Accepted -> orders.moveState(order, from = result.from, to = result.to)
    is TransitionResult.Rejected -> logger.info { "Cancellation of ${order.id} refused: ${result.reason.message}" }
}
```

Choose by what the caller would do with a rejection. If the answer is "turn it into a status code", `fireOrThrow` says so in one line. If it is "try
something else", handle both arms.

`canFire` answers the same question without running any effect.

## What the definition check catches

`stateMachine { }` refuses a definition that does not hold together, throwing `StateMachineDefinitionException` where the machine is built:

| Refused                                        | Almost always means                                      |
|------------------------------------------------|----------------------------------------------------------|
| No `initial`, or two of them                   | A copy-paste, or a merge                                 |
| A state declared twice                         | Two people added moves to the same state separately      |
| A move to a state nobody declared              | A typo, or a state that was meant to be `final`          |
| A state listed `final` with moves out of it    | The lifecycle grew and nobody removed it from `final(…)` |
| A dead end: nothing leaves it, and not `final` | A missing move, or a missing `final(…)` entry            |
| An unreachable state                           | **A missing transition**, not a state to delete          |
| Two moves one event could both trigger         | A `rel` was changed instead of the event                 |

The last two are worth reading twice. An unreachable state is a state someone meant to be able to get to; deleting it to make the build pass throws
away the requirement. And "two moves one event could both trigger" includes a specific move declared after a sealed parent. The specific one would
never fire, and being told so at startup beats debugging it in production.

## Publishing the moves as links

The machine already knows which transitions are open to a subject. That is exactly what `_links` is for, so let it answer:

```kotlin
// -adapters/web/order/OrderRoutes.kt
val moves = orderFlow.transitionLinks(order, order.state) { "/orders/${order.id}/${it.rel}" }

call.respond(
    resource(order.toResponse()) {
        link("self", "/orders/${order.id}")
        links(moves)
    },
)
```

`resource { }` takes a non-suspending block and guards may suspend, so build the links first. `withTransitions` is the one-expression form when the
moves are the only links being computed. Load `ktor-toolkit:hateoas` for the envelope.

The payoff is that the client stops reimplementing the lifecycle. It does not ask whether the order is `PLACED` and whether it has lines; it looks
for a `pay` link, and its absence is the answer. `rel` is what gets published. Set it when the event is named for the domain and the relation should
be named for the client.

## Persistence

**The state is a column; the machine is code.** Never serialize a machine, never store one, never build one from a database row. Persisting the graph
means a deployment can disagree with the data about what the rules are.

**Write the new state conditionally on the old one.** Two `POST /orders/42/pay` requests arriving together both load `PLACED`, both pass the guard,
and both save `PAID`, so the receipt is issued twice. The machine cannot see the race; the write can:

```kotlin
override suspend fun moveState(order: Order, from: OrderState, to: OrderState): Order? =
    suspendTransaction(database) {
        val moved = Orders.update({ (Orders.id eq order.id.value) and (Orders.state eq from) }) { it[state] = to }
        if (moved == 0) null else order.copy(state = to)
    }
```

Zero rows updated means someone else moved the order first; the use case turns that into a domain exception that maps to `409`. A `version` column
does the same job when other fields change in the same write.

The aggregate stores its state like any other field, and the adapter maps it like any other enum. Adding a state to the enum is a schema change if
the column is constrained. Load `ktor-toolkit:migrations` before widening a CHECK constraint or a Postgres enum type, and remember that rows written
by the old version still hold the old values.

## Reporting a rejection to the client

`IllegalTransitionException` carries no HTTP status, deliberately: a `-core` type that picks status codes has become a web layer. Choose the status
once, in the adapter's shared `commonProblems()`:

```kotlin
// -adapters/web/CommonProblems.kt
on<IllegalTransitionException> { failure ->
    ProblemDetail.fromStatus(HttpStatusCode.Conflict, "The resource cannot make that move now", mapOf("reason" to failure.reason.message))
}
```

`409 Conflict` is nearly always right: the request was well formed, and it disagreed with the state the resource is actually in. The guard's reason
is written for the client, so it travels in `properties`. Map `OrderChangedConcurrentlyException` to `409` beside the order's other mappings. Load
`ktor-toolkit:problem-details` for the envelope and the mapping DSL.

## Testing a machine

A machine needs no server and no container. It is a plain object. Load `ktor-toolkit:tests` for the conventions; what is specific here is **what to
assert**:

- **The moves**, both ways: an accepted one reports the right target, a refused one reports the right reason. `fire` returning
  `Rejected(GuardFailed("…"))` is a better assertion than `shouldThrow`, because it pins the message the client will read.
- **The affordances.** `availableFor(subject, state)` is what the API publishes, so test it directly rather than inferring it from the transitions.
- **The use case**, for what happens around the move: that a refused event writes nothing, and that a lost race, where `moveState` returns `null`,
  issues no receipt.
- **The conditional write**, against a real database: two `moveState` calls from the same `from`, and exactly one wins.

You do not need a test that the definition is coherent. Building the machine is that test, and it runs at class-load whether you write one or not.

## Common mistakes

| Mistake                                                             | Why it hurts                                                                    |
|---------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `if (order.state != PLACED) throw …` in a use case                  | The rule now lives in two places, and only one of them is checked at startup    |
| A `when (order.state)` chain deciding what may happen next          | That is the machine, written imperatively and without the coherence check       |
| A guard that queries a repository the caller already loaded from    | Doubles the I/O, and makes `availableFor` expensive enough that nobody calls it |
| A guard that reaches for the event's payload                        | Impossible by design; it is validation; see the boundary above                  |
| Mutating the subject inside an effect                               | The machine's contract is that it returns the next state and changes nothing    |
| I/O in `effect`, `onEnter` or `onTransition`                        | Runs before the save; a failed or lost write leaves the side effect behind      |
| `save(order.copy(state = next))` with no condition on the old state | Two concurrent requests both make the move, and both run its consequences       |
| Firing an event from a route                                        | The lifecycle rule leaves `-core`; fire in the use case                         |
| Persisting or deserializing a machine                               | The rules can then disagree with the deployment that enforces them              |
| Building the machine inside a function                              | The definition check moves from startup to the first request that runs it       |
| Two states for a boolean                                            | Ceremony without a rule; use the boolean                                        |
| Deleting an unreachable state to make the build pass                | Throws away the requirement instead of adding the transition that was missing   |
| `_links` written by hand beside a machine that knows the answer     | Two copies of the lifecycle, and the hand-written one goes stale first          |
| A status code chosen in `-core`                                     | The domain has become a web layer; map the exception in the adapter             |
