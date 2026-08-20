package com.heihun.easytiermd3.domain.model

import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.ProxyNetworkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyTierConfigCodecTest {

    @Test
    fun `encode then decode keeps all fields`() {
        val config = EasyTierConfig(
            networkName = "HeiHun Network",
            networkSecret = "p@ss\"word\\x",
            hostname = "Android Phone",
            startNodes = listOf("tcp://1.2.3.4:11010", "udp://example.com:11010"),
            ipv4 = "10.144.0.2",
            cidr = "10.144.0.0/16",
            listenPort = 12000,
        )

        val toml = EasyTierConfigCodec.encode(config)
        val decoded = EasyTierConfigCodec.decode(toml).getOrThrow()

        assertEquals(config.networkName, decoded.networkName)
        assertEquals(config.networkSecret, decoded.networkSecret)
        assertEquals(config.hostname, decoded.hostname)
        assertEquals(config.startNodes, decoded.startNodes)
        assertEquals(config.ipv4, decoded.ipv4)
        assertEquals(config.cidr, decoded.cidr)
        assertEquals(config.listenPort, decoded.listenPort)
    }

    @Test
    fun `encode emits dhcp when no static ipv4`() {
        val toml = EasyTierConfigCodec.encode(
            EasyTierConfig(networkName = "N", listenPort = 11010)
        )
        assertTrue(toml.contains("dhcp = true"))
        assertFalse(toml.contains("ipv4 ="))
    }

    @Test
    fun `proxy network round trip`() {
        val config = EasyTierConfig(
            networkName = "HeiHun",
            listenPort = 11010,
            proxyNetworks = listOf(
                ProxyNetworkConfig(cidr = "192.168.1.0/24"),
                ProxyNetworkConfig(
                    cidr = "172.16.0.0/16",
                    mappedCidr = "10.233.0.0/16",
                    allow = listOf("host1", "host2"),
                ),
            ),
        )

        val toml = EasyTierConfigCodec.encode(config)
        val decoded = EasyTierConfigCodec.decode(toml).getOrThrow()

        assertEquals(config.proxyNetworks, decoded.proxyNetworks)
    }

    @Test
    fun `merge keeps unknown top-level keys and flags section`() {
        val original = """
            instance_name = "easy-tiermd3-abc12345"
            hostname = "Old Name"
            [flags]
            enable_encryption = true
            data_compress_algo = "zstd"
        """.trimIndent()

        val updated = EasyTierConfig(
            networkName = "New Net",
            hostname = "New Name",
            listenPort = 11010,
        )
        val merged = EasyTierConfigCodec.merge(original, updated)

        assertTrue(merged.contains("hostname = \"New Name\""))
        assertTrue(merged.contains("instance_name = \"easy-tiermd3-abc12345\""))
        assertTrue(merged.contains("[flags]"))
        assertTrue(merged.contains("enable_encryption = true"))
        assertTrue(merged.contains("data_compress_algo = \"zstd\""))
        assertTrue(merged.contains("network_name = \"New Net\""))
        assertFalse(merged.contains("Old Name"))
    }

    @Test
    fun `merge rebuilds proxy networks and keeps peer public keys`() {
        val original = """
            dhcp = true
            [network_identity]
            network_name = "HeiHun"
            unknown_identity_key = "keep-me"
            [[proxy_network]]
            cidr = "10.0.0.0/8"
            [[peer]]
            uri = "tcp://a.example.com:11010"
            peer_public_key = "PUB_KEY_ABC"
        """.trimIndent()

        val updated = EasyTierConfig(
            networkName = "HeiHun",
            listenPort = 11010,
            startNodes = listOf("tcp://a.example.com:11010"),
            proxyNetworks = listOf(ProxyNetworkConfig(cidr = "192.168.9.0/24")),
        )
        val merged = EasyTierConfigCodec.merge(original, updated)

        assertTrue(merged.contains("cidr = \"192.168.9.0/24\""))
        assertFalse(merged.contains("10.0.0.0/8"))
        assertTrue(merged.contains("peer_public_key = \"PUB_KEY_ABC\""))
        assertTrue(merged.contains("unknown_identity_key = \"keep-me\""))
    }

    @Test
    fun `merge with blank original equals encode`() {
        val config = EasyTierConfig(
            networkName = "N",
            ipv4 = "10.1.1.2",
            cidr = "10.1.1.0/24",
            listenPort = 11010,
        )
        assertEquals(EasyTierConfigCodec.encode(config), EasyTierConfigCodec.merge("", config))
    }

    @Test
    fun `merge round trip decode keeps updated fields`() {
        val original = """
            [flags]
            enable_ipv6 = true
            [[peer]]
            uri = "tcp://a.example.com:11010"
            peer_public_key = "PUB_KEY_XYZ"
        """.trimIndent()
        val updated = EasyTierConfig(
            networkName = "N2",
            hostname = "Phone",
            startNodes = listOf("tcp://a.example.com:11010", "tcp://b.example.com:11010"),
            ipv4 = "10.144.0.2",
            cidr = "10.144.0.0/24",
            listenPort = 11010,
        )

        val merged = EasyTierConfigCodec.merge(original, updated)
        val decoded = EasyTierConfigCodec.decode(merged).getOrThrow()

        assertEquals("N2", decoded.networkName)
        assertEquals("Phone", decoded.hostname)
        assertEquals("10.144.0.2", decoded.ipv4)
        assertEquals("10.144.0.0/24", decoded.cidr)
        assertEquals(updated.startNodes, decoded.startNodes)
    }

    @Test
    fun `decode handles missing optional fields with defaults`() {
        val toml = """
            [network]
            network_name = "Simple"

            [instance]
            hostname = "Android"
        """.trimIndent()

        val decoded = EasyTierConfigCodec.decode(toml).getOrThrow()

        assertEquals("Simple", decoded.networkName)
        assertEquals("Android", decoded.hostname)
        assertEquals(11010, decoded.listenPort)
        assertTrue(decoded.startNodes.isEmpty())
    }

    @Test
    fun `decode reports line number for invalid line`() {
        val toml = """
            [network]
            network_name = "Test"
            this is not valid toml
        """.trimIndent()

        val result = EasyTierConfigCodec.decode(toml)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as EasyTierConfigCodec.ConfigParseException
        assertEquals(3, error.lineNumber)
        assertTrue(error.toDisplayMessage().startsWith("第 3 行"))
    }

    @Test
    fun `decode fails when network_name missing`() {
        val toml = "[instance]\nhostname = \"Android\""
        val result = EasyTierConfigCodec.decode(toml)
        assertTrue(result.isFailure)
    }

    @Test
    fun `round trip preserves raw decoded structure`() {
        val toml = """
            [network]
            network_name = "My Network"
            network_secret = "secret"
            cidr = "10.0.0.0/24"

            [instance]
            hostname = "Phone"
            listen_port = 7777

            [[peer]]
            uri = "tcp://a.example.com:11010"
        """.trimIndent()

        val decoded = EasyTierConfigCodec.decode(toml).getOrThrow()
        assertEquals(listOf("tcp://a.example.com:11010"), decoded.startNodes)
        assertEquals(7777, decoded.listenPort)
        assertEquals("10.0.0.0/24", decoded.cidr)
    }
}