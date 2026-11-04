package com.github.joaoseidel.ktor.toolkit.nats.api

import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.client.Nats
import io.nats.client.Options
import kotlinx.coroutines.CompletableDeferred

/** Where NATS is and how to authenticate. [username] and [password] travel together; leave both `null` for an open server. */
data class NatsSettings(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val connectionName: String? = null,
)

/**
 * The one connection a service shares, opened in the background so that a NATS outage cannot stop the service from booting.
 *
 * Reconnects forever. Safe to share across coroutines. Close it at shutdown.
 */
class NatsClient private constructor(
    private val connecting: CompletableDeferred<Connection>,
) : AutoCloseable {
    /** Suspends until the first connection is open, however long that takes: bound the wait where a caller is waiting on it. */
    suspend fun connection(): Connection = connecting.await()

    /** The connection, or `null` while the first one is still being opened. Does not say whether it is currently up. */
    internal fun connectionOrNull(): Connection? = if (connecting.isCompleted) connecting.getCompleted() else null

    /** Closes the connection, or abandons the attempt at opening one. */
    override fun close() {
        connectionOrNull()?.close()
        connecting.cancel()
    }

    companion object {
        /** Starts connecting and returns at once. Never throws for an unreachable server. */
        fun connect(settings: NatsSettings): NatsClient {
            val connecting = CompletableDeferred<Connection>()
            val options =
                Options
                    .Builder()
                    .server(settings.url)
                    .maxReconnects(-1)
                    .connectionListener { connection, event ->
                        if (event == ConnectionListener.Events.CONNECTED) connecting.complete(connection)
                    }.apply {
                        settings.connectionName?.let(::connectionName)
                        settings.username?.let { userInfo(it, settings.password) }
                    }.build()

            Nats.connectAsynchronously(options, true)
            return NatsClient(connecting)
        }
    }
}
