package com.jeerovan.comfer

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupGateTest {
    @Test
    fun awaitReadySuspendsUntilReady() = runBlocking {
        val gate = StartupGate()
        val waiter = async {
            gate.awaitReady()
            true
        }

        assertFalse(waiter.isCompleted)
        gate.markReady()

        assertTrue(waiter.await())
        assertTrue(gate.isReady)
    }

    @Test
    fun failureIsExplicitAndCanBeRetried() = runBlocking {
        val gate = StartupGate()
        val failure = IllegalStateException("migration failed")
        gate.markFailed(failure)

        val retry = async {
            gate.awaitReady()
            true
        }
        assertFalse(retry.isCompleted)
        assertTrue(gate.state.value is StartupState.Failed)

        gate.markInitializing()
        assertFalse(retry.isCompleted)

        gate.markReady()
        assertTrue(retry.await())
    }
}
