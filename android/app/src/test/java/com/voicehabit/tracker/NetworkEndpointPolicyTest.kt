package com.voicehabit.tracker

import com.voicehabit.tracker.core.network.NetworkEndpointPolicy
import com.voicehabit.tracker.core.network.NetworkEndpointPolicy.Endpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEndpointPolicyTest {

    @Test
    fun allowsDefaultEmulatorLoopback() {
        val endpoint = NetworkEndpointPolicy.evaluate("http://10.0.2.2:8000/")

        assertTrue(endpoint is Endpoint.Allowed)
        assertEquals("http://10.0.2.2:8000/", (endpoint as Endpoint.Allowed).baseUrl)
    }

    @Test
    fun allowsLoopbackWithoutPath() {
        assertEquals(
            "http://127.0.0.1:8000/",
            allowedBaseUrl("http://127.0.0.1:8000")
        )
        assertEquals("http://localhost:8000/", allowedBaseUrl("http://localhost:8000"))
        assertEquals("http://[::1]:8000/", allowedBaseUrl("http://[::1]:8000/"))
    }

    @Test
    fun allowsHttpsAndAddsTrailingSlash() {
        assertEquals("https://api.example.com/", allowedBaseUrl("https://api.example.com"))
        assertEquals("https://api.example.com/", allowedBaseUrl("https://api.example.com/"))
        assertEquals("https://api.example.com/v1/", allowedBaseUrl("https://api.example.com/v1"))
    }

    @Test
    fun normalizesSchemeCaseAndKeepsPort() {
        assertEquals("https://api.example.com:8443/", allowedBaseUrl("HTTPS://api.example.com:8443"))
    }

    @Test
    fun rejectsCleartextToRealHost() {
        val endpoint = NetworkEndpointPolicy.evaluate("http://192.168.1.10:8000/")

        assertTrue(endpoint is Endpoint.Rejected)
        assertTrue((endpoint as Endpoint.Rejected).reason.contains("10.0.2.2"))
    }

    @Test
    fun rejectsLoopbackLookalikeHost() {
        assertTrue(NetworkEndpointPolicy.evaluate("http://10.0.2.2.evil.com/") is Endpoint.Rejected)
        assertTrue(NetworkEndpointPolicy.evaluate("http://localhost.evil.com/") is Endpoint.Rejected)
    }

    @Test
    fun rejectsBlankAndSchemelessUrls() {
        assertTrue(NetworkEndpointPolicy.evaluate(null) is Endpoint.Rejected)
        assertTrue(NetworkEndpointPolicy.evaluate("   ") is Endpoint.Rejected)
        assertTrue(NetworkEndpointPolicy.evaluate("api.example.com") is Endpoint.Rejected)
        assertTrue(NetworkEndpointPolicy.evaluate("10.0.2.2:8000") is Endpoint.Rejected)
    }

    @Test
    fun rejectsCredentialsMissingHostAndOtherSchemes() {
        assertTrue(NetworkEndpointPolicy.evaluate("https://user:pass@api.example.com/") is Endpoint.Rejected)
        assertTrue(NetworkEndpointPolicy.evaluate("https:///voice") is Endpoint.Rejected)
        assertTrue(NetworkEndpointPolicy.evaluate("ws://api.example.com/") is Endpoint.Rejected)
        assertTrue(NetworkEndpointPolicy.evaluate("ftp://api.example.com/") is Endpoint.Rejected)
    }

    @Test
    fun rejectsMalformedUrl() {
        assertTrue(NetworkEndpointPolicy.evaluate("https://api example.com/") is Endpoint.Rejected)
    }

    @Test
    fun isAllowedMirrorsEvaluate() {
        assertTrue(NetworkEndpointPolicy.isAllowed("https://api.example.com"))
        assertFalse(NetworkEndpointPolicy.isAllowed("http://api.example.com"))
    }

    private fun allowedBaseUrl(raw: String): String {
        val endpoint = NetworkEndpointPolicy.evaluate(raw)
        assertTrue("Ожидался Allowed для $raw, получено $endpoint", endpoint is Endpoint.Allowed)
        return (endpoint as Endpoint.Allowed).baseUrl
    }
}
