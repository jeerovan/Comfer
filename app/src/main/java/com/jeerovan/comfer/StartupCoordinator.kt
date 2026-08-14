package com.jeerovan.comfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

sealed interface StartupState {
    data object Initializing : StartupState
    data object Ready : StartupState
    data class Failed(val cause: Throwable) : StartupState
}

/**
 * Process-wide barrier for data migration and the initial settings snapshot.
 *
 * Activities may render their lightweight default state while initialization
 * runs, but repositories and view models must wait for [awaitReady] before
 * reading or writing migrated data.
 */
internal class StartupGate {
    private val _state = MutableStateFlow<StartupState>(StartupState.Initializing)
    val state = _state.asStateFlow()
    val isReady: Boolean
        get() = _state.value is StartupState.Ready

    suspend fun awaitReady() {
        state.first { it is StartupState.Ready }
    }

    fun markInitializing() {
        _state.value = StartupState.Initializing
    }

    fun markReady() {
        _state.value = StartupState.Ready
    }

    fun markFailed(cause: Throwable) {
        _state.value = StartupState.Failed(cause)
    }
}

object StartupCoordinator {
    private val gate = StartupGate()
    val state = gate.state
    val isReady: Boolean
        get() = gate.isReady

    suspend fun awaitReady() = gate.awaitReady()
    internal fun markInitializing() = gate.markInitializing()
    internal fun markReady() = gate.markReady()
    internal fun markFailed(cause: Throwable) = gate.markFailed(cause)
}
