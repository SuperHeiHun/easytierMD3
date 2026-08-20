package com.heihun.easytiermd3.core.bridge

import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyTierCoreManagerTest {

    private val config = EasyTierConfig(
        networkName = "Test Network",
        hostname = "Test Phone",
        listenPort = 11010,
    )

    @Test
    fun `start transitions to Running`() {
        runBlocking {
            val manager = EasyTierCoreManager(FakeEasyTierCore())
            val result = manager.start(config)
            assertTrue(result.isSuccess)
            withTimeout(10_000) {
                manager.connectionState.first { it is EasyTierConnectionState.Running }
            }
        }
    }

    @Test
    fun `double start is rejected`() {
        runBlocking {
            val manager = EasyTierCoreManager(FakeEasyTierCore())
            manager.start(config)
            withTimeout(10_000) {
                manager.connectionState.first { it is EasyTierConnectionState.Running }
            }
            val second = manager.start(config)
            assertTrue(second.isFailure)
        }
    }

    @Test
    fun `stop transitions to Stopped`() {
        runBlocking {
            val manager = EasyTierCoreManager(FakeEasyTierCore())
            manager.start(config)
            withTimeout(10_000) {
                manager.connectionState.first { it is EasyTierConnectionState.Running }
            }
            val result = manager.stop()
            assertTrue(result.isSuccess)
            withTimeout(10_000) {
                manager.connectionState.first { it is EasyTierConnectionState.Stopped }
            }
        }
    }

    @Test
    fun `stop when already stopped is a no-op success`() {
        runBlocking {
            val manager = EasyTierCoreManager(FakeEasyTierCore())
            assertTrue(manager.stop().isSuccess)
            assertEquals(EasyTierConnectionState.Stopped, manager.connectionState.value)
        }
    }

    @Test
    fun `invalid config produces Error state`() {
        runBlocking {
            val manager = EasyTierCoreManager(FakeEasyTierCore())
            val result = manager.start(config.copy(networkName = "  "))
            assertTrue(result.isFailure)
            withTimeout(10_000) {
                manager.connectionState.first { it is EasyTierConnectionState.Error }
            }
        }
    }

    @Test
    fun `stop clears error state back to Stopped`() {
        runBlocking {
            val manager = EasyTierCoreManager(FakeEasyTierCore())
            manager.start(config.copy(networkName = "  "))
            withTimeout(10_000) {
                manager.connectionState.first { it is EasyTierConnectionState.Error }
            }
            assertTrue(manager.stop().isSuccess)
            assertEquals(EasyTierConnectionState.Stopped, manager.connectionState.value)
        }
    }
}