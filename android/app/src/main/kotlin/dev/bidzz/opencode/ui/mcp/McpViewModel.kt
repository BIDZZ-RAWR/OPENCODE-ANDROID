package dev.bidzz.opencode.ui.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.McpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class McpUiState(
    val loading: Boolean = true,
    val servers: List<McpServer> = emptyList(),
    /** Servers with a connect or disconnect in flight, so their button cannot be tapped twice. */
    val busy: Set<String> = emptySet(),
    val failure: ApiResult.Failure? = null,
    /** A message with no failure behind it, e.g. the engine never started. */
    val failureKey: Int? = null,
)

/**
 * MCP servers of the current project.
 *
 * Loads on demand, like the session list: the engine owns the state, and a server can change
 * between connected and failed on its own, so a cached copy would be stale the moment it is read.
 */
class McpViewModel(
    private val api: EngineApi,
    private val workspace: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow(McpUiState())
    val state: StateFlow<McpUiState> = _state.asStateFlow()

    fun load() {
        val handle = api.handle() ?: run {
            _state.value = _state.value.copy(loading = false, failureKey = R.string.error_engine_not_running)
            return
        }
        _state.value = _state.value.copy(loading = true, failure = null, failureKey = null)
        viewModelScope.launch {
            when (val result = handle.mcp.list(workspace())) {
                is ApiResult.Success -> _state.value = _state.value.copy(loading = false, servers = result.value)
                is ApiResult.Failure -> _state.value = _state.value.copy(loading = false, failure = result)
            }
        }
    }

    /** Disconnects a connected server, and connects any other. Reloads afterwards for the new status. */
    fun toggle(server: McpServer) {
        val handle = api.handle() ?: return
        if (server.name in _state.value.busy) return
        _state.value = _state.value.copy(busy = _state.value.busy + server.name, failure = null)
        viewModelScope.launch {
            val result = if (server.status.status == "connected") {
                handle.mcp.disconnect(workspace(), server.name)
            } else {
                handle.mcp.connect(workspace(), server.name)
            }
            _state.value = _state.value.copy(
                busy = _state.value.busy - server.name,
                failure = result as? ApiResult.Failure,
            )
            // The new status is the engine's to report; ask rather than guess.
            when (val list = handle.mcp.list(workspace())) {
                is ApiResult.Success -> _state.value = _state.value.copy(servers = list.value)
                is ApiResult.Failure -> if (result is ApiResult.Success) _state.value = _state.value.copy(failure = list)
            }
        }
    }
}
