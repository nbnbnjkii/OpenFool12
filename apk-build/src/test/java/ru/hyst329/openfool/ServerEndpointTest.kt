package ru.hyst329.openfool

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class ServerEndpointTest {
    @Test fun requestsUsePublicIpAndHttpsWithoutDnsDependency() {
        val endpoint=URI(ServerEndpoint.BASE_URL+"/auth/login")
        assertEquals("https",endpoint.scheme)
        assertEquals("46.30.41.204",endpoint.host)
        assertEquals("/v1/auth/login",endpoint.path)
        assertNull(endpoint.userInfo)
        assertNull(endpoint.query)
    }
}
