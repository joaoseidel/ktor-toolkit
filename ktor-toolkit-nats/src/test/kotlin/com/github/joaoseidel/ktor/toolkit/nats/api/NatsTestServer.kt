package com.github.joaoseidel.ktor.toolkit.nats.api

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/** One server per run, because a stream's name isolates a test as well as a container would, and costs nothing to start. */
object NatsTestServer {
    private val container: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse("nats:2.15.2-alpine"))
            .withCommand("nats-server", "-js")
            .withExposedPorts(4222)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
            .also { it.start() }
    }

    val url: String by lazy { "nats://${container.host}:${container.getMappedPort(4222)}" }

    /** A server on a port the caller chose, not started, so a client can be connecting to it before it exists. */
    fun lateServer(port: Int): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("nats:2.15.2-alpine"))
            .withCommand("nats-server", "-js")
            .withExposedPorts(4222)
            .apply { portBindings = listOf("$port:4222") }
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))

    fun client(): NatsClient = NatsClient.connect(NatsSettings(url))
}

fun uniqueName(): String =
    UUID
        .randomUUID()
        .toString()
        .replace("-", "")
        .take(12)
        .uppercase()

fun streamNamed(name: String = uniqueName()): StreamSpec =
    StreamSpec(name = name, subjects = listOf("$name.>"), maxAge = 1.hours, maxBytes = 1024 * 1024)
