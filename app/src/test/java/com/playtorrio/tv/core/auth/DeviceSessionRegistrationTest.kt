package com.playtorrio.tv.core.auth

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSessionRegistrationTest {

    @Test
    fun `builds official TV client registration payload`() {
        val params = buildDeviceRegistrationParams(
            installationId = "playtorrio-tv-installation",
            clientVersion = "0.7.20-beta",
            metadata = DeviceClientMetadata(
                deviceName = "Living Room TV",
                platform = "Android TV 14"
            )
        )

        assertEquals("playtorrio-tv-installation", params.getValue("p_installation_id").jsonPrimitive.content)
        assertEquals("PlayTorrio TV", params.getValue("p_client_name").jsonPrimitive.content)
        assertEquals("0.7.20-beta", params.getValue("p_client_version").jsonPrimitive.content)
        assertEquals("Android TV 14", params.getValue("p_platform").jsonPrimitive.content)
        assertEquals("Living Room TV", params.getValue("p_device_name").jsonPrimitive.content)
    }

    @Test
    fun `prefers configured TV name and falls back to hardware name`() {
        assertEquals(
            "Living Room TV",
            selectDeviceName(
                configuredName = " Living Room TV ",
                manufacturer = "Sony",
                model = "BRAVIA 4K",
                fallback = "Android TV"
            )
        )
        assertEquals(
            "Sony BRAVIA 4K",
            selectDeviceName(
                configuredName = null,
                manufacturer = "Sony",
                model = "BRAVIA 4K",
                fallback = "Android TV"
            )
        )
        assertEquals(
            "Google TV Streamer",
            selectDeviceName(
                configuredName = "",
                manufacturer = "Google",
                model = "Google TV Streamer",
                fallback = "Android TV"
            )
        )
    }
}
