package dev.bidzz.opencode.core.api

import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * A configured MCP server and whether it is connected.
 *
 * The engine nests the state: `"status": {"status": "connected"}`, with an `error` beside it for
 * `failed` and `needs_auth`.
 */
@Serializable
data class McpServer(
    val name: String,
    val status: McpStatus,
    /** The connected server's own display title; absent while disconnected. */
    val title: String? = null,
)

@Serializable
data class McpStatus(
    /** `connected`, `pending`, `disabled`, `failed` or `needs_auth`. */
    val status: String,
    val error: String? = null,
)

@Serializable
internal data class McpListResponse(val data: List<McpServer>)

/**
 * MCP servers, scoped to a project directory.
 *
 * Connect and disconnect are runtime overrides: they last until the engine restarts. What
 * persists is the engine's config, which this app does not edit here.
 */
class McpApi(private val client: EngineClient) {

    suspend fun list(directory: String): ApiResult<List<McpServer>> {
        val request = client.request("/api/mcp?${directoryQuery(directory)}").get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<McpListResponse>(raw).data
        }
    }

    suspend fun connect(directory: String, server: String): ApiResult<Unit> = post(directory, server, "connect")

    suspend fun disconnect(directory: String, server: String): ApiResult<Unit> = post(directory, server, "disconnect")

    private suspend fun post(directory: String, server: String, action: String): ApiResult<Unit> {
        val request = client.request("/api/experimental/mcp/${encode(server)}/$action?${directoryQuery(directory)}")
            .post(client.emptyBody())
            .build()
        return client.executeUnit(request)
    }

    private fun directoryQuery(directory: String) = "directory=${encode(directory)}"

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
